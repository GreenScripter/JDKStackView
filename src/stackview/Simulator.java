package stackview;

import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import stackview.Stack.StackOp;

public class Simulator {

	InstructionSpec insts;
	public LocalVariables locals;
	public Stack stack;
	public int instruction;
	StackOp[] initParams;
	public State state = State.INIT;;
	public String errorMessage;

	public static enum State {

		INIT, PUSH, POP, JUMP, FINISHED(true), ERRORED(true);

		public final boolean done;

		State() {
			this(false);
		}

		State(boolean done) {
			this.done = done;
		}
	}

	public Simulator(InstructionSpec insts) {
		this.insts = insts;
		stack = new Stack(insts.stackSize);
		locals = new LocalVariables(insts.locals);
		initParams = Stack.fromMethodArguments(insts.descriptor);
		if (!insts.staticMethod) {
			StackOp[] initParams = new StackOp[this.initParams.length + 1];
			System.arraycopy(this.initParams, 0, initParams, 1, this.initParams.length);
			initParams[0] = StackOp.REFERENCE;
			this.initParams = initParams;
		}
	}

	public Simulator(Simulator s) {
		become(s);
	}

	public void become(Simulator s) {
		this.insts = s.insts;
		this.locals = new LocalVariables(s.locals);
		this.stack = new Stack(s.stack);
		this.instruction = s.instruction;
		this.initParams = s.initParams;
		this.state = s.state;
		this.errorMessage = s.errorMessage;
		this.popped = new ArrayList<>(s.popped);
		this.popped2 = new ArrayList<>(s.popped2);
	}

	public void initLocals() {
		try {
			ensureState(State.INIT);
			int offset = 0;
			for (StackOp s : initParams) {
				switch (s) {

					case DOUBLE:
						locals.set(offset, new EntryValue(EntryType.DOUBLE, -1), 0);
						offset += 2;
						break;
					case FLOAT:
						locals.set(offset, new EntryValue(EntryType.FLOAT, -1), 0);
						offset++;
						break;
					case INT:
						locals.set(offset, new EntryValue(EntryType.INT, -1), 0);
						offset++;
						break;
					case LONG:
						locals.set(offset, new EntryValue(EntryType.LONG, -1), 0);
						offset += 2;
						break;
					case REFERENCE:
						locals.set(offset, new EntryValue(EntryType.REFERENCE, -1), 0);
						offset++;
						break;
					case JUMP:
					case FRAME:
					case ANY_1:
					case ANY_2:
					case CLEAR:
					case REFERENCE_OR_RETURN_ADDRESS:
					case RET:
					case RETURN_ADDRESS:
					case THROW:
					default:
						throw new UnsupportedOperationException("Unknown Stack op " + s);

				}
			}
			state = State.POP;
		} catch (Exception e) {
			e.printStackTrace();
			state = State.ERRORED;
			errorMessage = e.getMessage();
		}
	}

	List<EntryValue> popped = new ArrayList<>();
	List<EntryValue[]> popped2 = new ArrayList<>();

	private List<EntryValue> getAllPopped() {
		if (popped2.isEmpty()) {
			return popped;
		} else {
			List<EntryValue> all = new ArrayList<>(popped);
			for (EntryValue[] v : popped2) {
				all.add(v[0]);
				all.add(v[1]);
			}
			return all;
		}
	}

	public void performPops() {
		try {
			ensureState(State.POP);
			popped.clear();
			popped2.clear();
			for (StackOp op : insts.alters.get(instruction).remove) {
				switch (op) {
					case ANY_1:
						popped.add(stack.popAny1());
						break;
					case ANY_2:
						popped2.add(stack.popAny2());
						break;
					case CLEAR:
						stack.entries.clear();
						break;
					case DOUBLE:
						popped.add(stack.pop(EntryType.DOUBLE));
						break;
					case FLOAT:
						popped.add(stack.pop(EntryType.FLOAT));
						break;
					case FRAME:
						throw new UnsupportedOperationException("Frame is add only.");
					case INT:
						popped.add(stack.pop(EntryType.INT));
						break;
					case JUMP:
						throw new UnsupportedOperationException("Jump is add only.");
					case LONG:
						popped.add(stack.pop(EntryType.LONG));
						break;
					case REFERENCE:
						popped.add(stack.pop(EntryType.REFERENCE));
						break;
					case REFERENCE_OR_RETURN_ADDRESS:
						popped.add(stack.popRefOrRet());
						break;
					case RET:
						throw new UnsupportedOperationException("Ret is add only.");
					case RETURN_ADDRESS:
						popped.add(stack.pop(EntryType.RETURN_ADDRESS));
						break;
					case THROW:
						throw new UnsupportedOperationException("Throw is add only.");
				}
			}
			state = State.PUSH;
		} catch (Exception e) {
			e.printStackTrace();
			state = State.ERRORED;
			errorMessage = e.getMessage();
		}
	}

	public void ensureState(State s) {
		if (state != s) {
			throw new IllegalStateException("States performed out of order. Expected " + s + " found " + state);
		}
	}

	public void performPushes() {
		try {
			ensureState(State.PUSH);
			StackAlter alter = insts.alters.get(instruction);
			AbstractInsnNode node = alter.node;
			state = State.JUMP;

			switch (node.getOpcode()) {
				case DUP -> {
					EntryValue value = popped.get(0);
					stack.push(value);
					stack.push(value.move(instruction));
					return;
				}
				case DUP_X1 -> {
					EntryValue value = popped.get(0);
					EntryValue value2 = popped.get(1);
					stack.push(value.move(instruction));
					stack.push(value2);
					stack.push(value);
					return;
				}
				case DUP_X2 -> {
					stack.push(popped.get(0).move(instruction));
					stack.push(popped.get(2));
					stack.push(popped.get(1));
					stack.push(popped.get(0));
					return;
				}
				case DUP2 -> {
					EntryValue[] value = popped2.get(0);
					stack.push2(value);
					stack.push2(new EntryValue[] { value[0].move(instruction), value[1].move(instruction) });
					return;
				}
				case DUP2_X1 -> {
					EntryValue[] value = popped2.get(0);
					stack.push2(new EntryValue[] { value[0].move(instruction), value[1].move(instruction) });
					stack.push(popped.get(0));
					stack.push2(value);
					return;
				}
				case DUP2_X2 -> {
					EntryValue[] value = popped2.get(0);
					stack.push2(new EntryValue[] { value[0].move(instruction), value[1].move(instruction) });
					stack.push2(popped2.get(1));
					stack.push2(value);
					return;
				}
				case SWAP -> {
					EntryValue value = popped.get(0);
					EntryValue value2 = popped.get(1);
					stack.push(value.move(instruction));
					stack.push(value2.move(instruction));
					return;
				}
			}
			for (StackOp op : alter.add) {
				switch (op) {
					case ANY_1:
						throw new UnsupportedOperationException("ANY_1 should be handled by special case " + insts.toString(insts.alters.get(instruction).node));
					case ANY_2:
						throw new UnsupportedOperationException("ANY_2 should be handled by special case " + insts.toString(insts.alters.get(instruction).node));
					case CLEAR:
						stack.entries.clear();
						break;
					case DOUBLE:
						stack.push(EntryType.DOUBLE, instruction, getAllPopped());
						break;
					case FLOAT:
						stack.push(EntryType.FLOAT, instruction, getAllPopped());
						break;
					case FRAME:
						handleFrame(insts.alters.get(instruction));
						break;
					case INT:
						stack.push(EntryType.INT, instruction, getAllPopped());
						break;
					case JUMP:
						// Performed in Jump step.
						break;
					case LONG:
						stack.push(EntryType.LONG, instruction, getAllPopped());
						break;
					case REFERENCE:
						stack.push(EntryType.REFERENCE, instruction, getAllPopped());
						break;
					case REFERENCE_OR_RETURN_ADDRESS:
						stack.push(popped.remove(popped.size() - 1));
						break;
					case RET:
						throw new UnsupportedOperationException("Ret is add only.");
					case RETURN_ADDRESS:
						stack.push(EntryType.RETURN_ADDRESS, instruction, getAllPopped());
						break;
					case THROW:
						break;
				}
			}
			handleKnownValues(alter);
			handleStore(alter);
			handleLoad(alter);
			handleReturn(alter);
		} catch (Exception e) {
			e.printStackTrace();
			state = State.ERRORED;
			errorMessage = e.getMessage();
		}
	}

	public void jumpToCatch(String type) {
		try {
			List<TryCatchBlockNode> tryCatches = new ArrayList<>(getActiveCatches());
			Collections.reverse(tryCatches);
			for (var tryCatch : tryCatches) {
				if ((tryCatch.type == null && type == null) || tryCatch.type.equals(type)) {
					performJump(tryCatch.handler.getLabel());
					return;
				}
			}
			state = State.FINISHED;
		} catch (Exception e) {
			e.printStackTrace();
			state = State.ERRORED;
			errorMessage = e.getMessage();
		}
	}

	public List<TryCatchBlockNode> getActiveCatches() {
		List<TryCatchBlockNode> active = new ArrayList<>();
		for (int i = 0; i < insts.alters.size(); i++) {
			if (insts.alters.get(i).node instanceof LabelNode label) {
				for (TryCatchBlockNode tryCatch : insts.tryCatchBlocks) {
					if (tryCatch.start.getLabel() == label.getLabel()) {
						active.add(tryCatch);
					}
					if (tryCatch.end.getLabel() == label.getLabel()) {
						active.remove(tryCatch);
					}
				}
			}
			if (i == instruction) {
				break;
			}
		}
		return active;
	}

	public String getLocalName(int local) {
		String name = null;
		LocalVariableNode setter = null;
		for (int i = 0; i < insts.alters.size(); i++) {
			if (insts.alters.get(i).node instanceof LabelNode label) {
				for (LocalVariableNode varName : insts.localsDebug) {
					if (varName.index == local) {
						if (varName.start.getLabel() == label.getLabel()) {
							name = varName.name;
							setter = varName;
						}
						if (setter == varName && varName.end.getLabel() == label.getLabel()) {
							name = null;
						}
					}
				}
			}
			if (i == instruction) {
				break;
			}
		}
		return name;
	}

	private void handleKnownValues(StackAlter sa) {
		AbstractInsnNode node = sa.node;

		switch (node.getOpcode()) {
			case NEW -> {
				stack.setTopKnown(0, "Type: " + ((TypeInsnNode) node).desc);
			}

			// Dynamic constants.
			case LDC -> {
				stack.setTopKnown(0, "" + ((LdcInsnNode) node).cst);
			}
			case BIPUSH, SIPUSH -> {
				stack.setTopKnown(0, "" + ((IntInsnNode) node).operand);
			}

			// Methods
			case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE -> {
				MethodInsnNode method = (MethodInsnNode) node;
				Type returnType = Type.getReturnType(method.desc);
				if (returnType.getSort() == Type.OBJECT) {
					stack.setTopKnown(0, "Type: " + returnType.getDescriptor());
				}
			}
			case INVOKEDYNAMIC -> {
				InvokeDynamicInsnNode method = (InvokeDynamicInsnNode) node;
				Type returnType = Type.getReturnType(method.desc);
				if (returnType.getSort() == Type.OBJECT) {
					stack.setTopKnown(0, "Type: " + returnType.getDescriptor());
				}
			}

			// Arrays
			case NEWARRAY -> {
				String extra = "";
				if (isInt(popped.get(0).getKnownValue())) {
					extra += " Length: " + popped.get(0).getKnownValue();
				}
				String type = "";
				switch (((IntInsnNode) node).operand) {
					case T_BOOLEAN:
						type = "[Z";
						break;
					case T_BYTE:
						type = "[B";
						break;
					case T_CHAR:
						type = "[C";
						break;
					case T_DOUBLE:
						type = "[D";
						break;
					case T_FLOAT:
						type = "[F";
						break;
					case T_INT:
						type = "[I";
						break;
					case T_LONG:
						type = "[J";
						break;
					case T_SHORT:
						type = "[S";
						break;
				}
				stack.setTopKnown(0, "Type: " + type + extra);
			}
			case ANEWARRAY -> {
				String extra = "";
				if (isInt(popped.get(0).getKnownValue())) {
					extra += " Length: " + popped.get(0).getKnownValue();
				}
				stack.setTopKnown(0, "Type: [L" + ((TypeInsnNode) node).desc + ";" + extra);
			}
			case ARRAYLENGTH -> {
				String old = popped.get(0).getKnownValue();
				if (old.contains(" Length: ")) {
					stack.setTopKnown(0, old.substring(old.lastIndexOf(" Length: ") + 9).split(",")[0]);
				}
			}

			case AALOAD -> {
				String old = popped.get(1).getKnownValue();
				if (old.startsWith("Type: [")) {
					if (old.contains(" Length: ")) {
						String lengths = old.substring(old.lastIndexOf(" Length: ") + 9);
						if (lengths.contains(",")) {
							stack.setTopKnown(0, "Type: " + old.substring(7, old.lastIndexOf(" Length: ")) + " Length: " + lengths.substring(lengths.indexOf(",") + 1));
						} else {
							stack.setTopKnown(0, "Type: " + old.substring(7, old.lastIndexOf(" Length: ")));
						}
					} else {
						stack.setTopKnown(0, "Type: " + old.substring(7));
					}
				}
			}

			case MULTIANEWARRAY -> {
				MultiANewArrayInsnNode n = (MultiANewArrayInsnNode) node;
				String dims = "";
				for (int i = 0; i < n.dims; i++) {
					String v = popped.get(n.dims - 1 - i).getKnownValue();
					if (isInt(v)) dims += v;
					if (i + 1 < n.dims) {
						dims += ",";
					}
				}
				stack.setTopKnown(0, "Type: " + n.desc + " Length: " + dims);

			}

			// Constants
			case ACONST_NULL -> {
				stack.setTopKnown(0, "Type: null");
			}
			case ICONST_M1 -> {
				stack.setTopKnown(0, "-1");
			}
			case ICONST_0 -> {
				stack.setTopKnown(0, "0");
			}
			case ICONST_1 -> {
				stack.setTopKnown(0, "1");
			}
			case ICONST_2 -> {
				stack.setTopKnown(0, "2");
			}
			case ICONST_3 -> {
				stack.setTopKnown(0, "3");
			}
			case ICONST_4 -> {
				stack.setTopKnown(0, "4");
			}
			case ICONST_5 -> {
				stack.setTopKnown(0, "5");
			}
			case LCONST_0 -> {
				stack.setTopKnown(0, "0");
			}
			case LCONST_1 -> {
				stack.setTopKnown(0, "1");
			}
			case FCONST_0 -> {
				stack.setTopKnown(0, "0.0");
			}
			case FCONST_1 -> {
				stack.setTopKnown(0, "1.0");
			}
			case FCONST_2 -> {
				stack.setTopKnown(0, "2.0");
			}
			case DCONST_0 -> {
				stack.setTopKnown(0, "0.0");
			}
			case DCONST_1 -> {
				stack.setTopKnown(0, "1.0");
			}
			// Operators
			case IADD -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseIntOrUnknown(parseInt(a) + parseInt(b), a, b, "+"));
			}
			case LADD -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseLongOrUnknown(parseLong(a) + parseLong(b), a, b, "+"));
			}
			case FADD -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseFloatOrUnknown(parseFloat(a) + parseFloat(b), a, b, "+"));
			}
			case DADD -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseDoubleOrUnknown(parseDouble(a) + parseDouble(b), a, b, "+"));
			}
			case ISUB -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseIntOrUnknown(parseInt(a) - parseInt(b), a, b, "-"));
			}
			case LSUB -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseLongOrUnknown(parseLong(a) - parseLong(b), a, b, "-"));
			}
			case FSUB -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseFloatOrUnknown(parseFloat(a) - parseFloat(b), a, b, "-"));
			}
			case DSUB -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseDoubleOrUnknown(parseDouble(a) - parseDouble(b), a, b, "-"));
			}
			case IMUL -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseIntOrUnknown(parseInt(a) * parseInt(b), a, b, "*"));
			}
			case LMUL -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseLongOrUnknown(parseLong(a) * parseLong(b), a, b, "*"));
			}
			case FMUL -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseFloatOrUnknown(parseFloat(a) * parseFloat(b), a, b, "*"));
			}
			case DMUL -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseDoubleOrUnknown(parseDouble(a) * parseDouble(b), a, b, "*"));
			}
			case IDIV -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseIntOrUnknown(parseInt(a) / parseInt(b), a, b, "/"));
			}
			case LDIV -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseLongOrUnknown(parseLong(a) / parseLong(b), a, b, "/"));
			}
			case FDIV -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseFloatOrUnknown(parseFloat(a) / parseFloat(b), a, b, "/"));
			}
			case DDIV -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseDoubleOrUnknown(parseDouble(a) / parseDouble(b), a, b, "/"));
			}
			case IREM -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseIntOrUnknown(parseInt(a) % parseInt(b), a, b, "%"));
			}
			case LREM -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseLongOrUnknown(parseLong(a) % parseLong(b), a, b, "%"));
			}
			case FREM -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseFloatOrUnknown(parseFloat(a) % parseFloat(b), a, b, "%"));
			}
			case DREM -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseDoubleOrUnknown(parseDouble(a) % parseDouble(b), a, b, "%"));
			}
			case INEG -> {
				var a = popped.get(0).getKnownValue();
				if (isInt(a)) stack.setTopKnown(0, "" + -parseInt(a));
			}
			case LNEG -> {
				var a = popped.get(0).getKnownValue();
				if (isLong(a)) stack.setTopKnown(0, "" + -parseLong(a));
			}
			case FNEG -> {
				var a = popped.get(0).getKnownValue();
				if (isFloat(a)) stack.setTopKnown(0, "" + -parseFloat(a));
			}
			case DNEG -> {
				var a = popped.get(0).getKnownValue();
				if (isDouble(a)) stack.setTopKnown(0, "" + -parseDouble(a));
			}
			case ISHL -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseIntOrUnknown(parseInt(a) << parseInt(b), a, b, "<<"));
			}
			case LSHL -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseLongOrUnknown(parseLong(a) << parseLong(b), a, b, "<<"));
			}
			case ISHR -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseIntOrUnknown(parseInt(a) >> parseInt(b), a, b, ">>"));
			}
			case LSHR -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseLongOrUnknown(parseLong(a) >> parseLong(b), a, b, ">>"));
			}
			case IUSHR -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseIntOrUnknown(parseInt(a) >>> parseInt(b), a, b, ">>>"));
			}
			case LUSHR -> {
				var b = popped.get(0).getKnownValue();
				var a = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseLongOrUnknown(parseLong(a) >>> parseLong(b), a, b, ">>>"));
			}
			case IAND -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseIntOrUnknown(parseInt(a) & parseInt(b), a, b, "&"));
			}
			case LAND -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseLongOrUnknown(parseLong(a) & parseLong(b), a, b, "&"));
			}
			case IOR -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseIntOrUnknown(parseInt(a) | parseInt(b), a, b, "|"));
			}
			case LOR -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseLongOrUnknown(parseLong(a) | parseLong(b), a, b, "|"));
			}
			case IXOR -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseIntOrUnknown(parseInt(a) ^ parseInt(b), a, b, "^"));
			}
			case LXOR -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				stack.setTopKnown(0, parseLongOrUnknown(parseLong(a) ^ parseLong(b), a, b, "^"));
			}
			// Casts			
			case I2L -> {
				var a = popped.get(0).getKnownValue();
				if (isInt(a)) stack.setTopKnown(0, "" + (long) parseInt(a));
			}
			case I2F -> {
				var a = popped.get(0).getKnownValue();
				if (isInt(a)) stack.setTopKnown(0, "" + (float) parseInt(a));
			}
			case I2D -> {
				var a = popped.get(0).getKnownValue();
				if (isInt(a)) stack.setTopKnown(0, "" + (double) parseInt(a));
			}
			case L2I -> {
				var a = popped.get(0).getKnownValue();
				if (isLong(a)) stack.setTopKnown(0, "" + (int) parseLong(a));
			}
			case L2F -> {
				var a = popped.get(0).getKnownValue();
				if (isLong(a)) stack.setTopKnown(0, "" + (float) parseLong(a));
			}
			case L2D -> {
				var a = popped.get(0).getKnownValue();
				if (isLong(a)) stack.setTopKnown(0, "" + (double) parseLong(a));
			}
			case F2I -> {
				var a = popped.get(0).getKnownValue();
				if (isFloat(a)) stack.setTopKnown(0, "" + (int) parseFloat(a));
			}
			case F2L -> {
				var a = popped.get(0).getKnownValue();
				if (isFloat(a)) stack.setTopKnown(0, "" + (long) parseFloat(a));
			}
			case F2D -> {
				var a = popped.get(0).getKnownValue();
				if (isFloat(a)) stack.setTopKnown(0, "" + (double) parseFloat(a));
			}
			case D2I -> {
				var a = popped.get(0).getKnownValue();
				if (isDouble(a)) stack.setTopKnown(0, "" + (int) parseDouble(a));
			}
			case D2L -> {
				var a = popped.get(0).getKnownValue();
				if (isDouble(a)) stack.setTopKnown(0, "" + (long) parseDouble(a));
			}
			case D2F -> {
				var a = popped.get(0).getKnownValue();
				if (isDouble(a)) stack.setTopKnown(0, "" + (float) parseDouble(a));
			}
			case I2B -> {
				var a = popped.get(0).getKnownValue();
				if (isInt(a)) stack.setTopKnown(0, "" + (byte) parseInt(a));
			}
			case I2C -> {
				var a = popped.get(0).getKnownValue();
				if (isInt(a)) stack.setTopKnown(0, "" + (int) (char) parseInt(a));
			}
			case I2S -> {
				var a = popped.get(0).getKnownValue();
				if (isInt(a)) stack.setTopKnown(0, "" + (short) parseInt(a));
			}

			// Compares
			case LCMP -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				if (isLong(a) && isLong(b)) {
					stack.setTopKnown(0, "" + Long.compare(parseLong(a), parseLong(b)));
				}
			}
			case FCMPL, FCMPG -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				if (isFloat(a) && isFloat(b)) {
					stack.setTopKnown(0, "" + Float.compare(parseFloat(a), parseFloat(b)));
				}
			}
			case DCMPL, DCMPG -> {
				var a = popped.get(0).getKnownValue();
				var b = popped.get(1).getKnownValue();
				if (isDouble(a) && isDouble(b)) {
					stack.setTopKnown(0, "" + Double.compare(parseDouble(a), parseDouble(b)));
				}
			}
		}
	}

	private boolean isInt(String s) {
		try {
			Integer.parseInt(s);
			return true;
		} catch (Exception e) {
		}
		return false;
	}

	private boolean isLong(String s) {
		try {
			Long.parseLong(s);
			return true;
		} catch (Exception e) {
		}
		return false;
	}

	private boolean isFloat(String s) {
		try {
			Float.parseFloat(s);
			return true;
		} catch (Exception e) {
		}
		return false;
	}

	private boolean isDouble(String s) {
		try {
			Double.parseDouble(s);
			return true;
		} catch (Exception e) {
		}
		return false;
	}

	private String parseIntOrUnknown(int v, String a, String b, String op) {
		String value = v + "";
		boolean aValid = false;
		boolean bValid = false;
		try {
			Integer.parseInt(a);
			aValid = true;
		} catch (Exception e) {
		}
		try {
			Integer.parseInt(b);
			bValid = true;
		} catch (Exception e) {
		}
		if (bValid && aValid) {
			return value;
		} else if (op == null) {
			return null;
		} else if (bValid) {
			return "Unknown" + op + b;
		} else if (aValid) {
			return a + op + "Unknown";
		} else {
			return null;
		}
	}

	private String parseLongOrUnknown(long v, String a, String b, String op) {
		String value = v + "";
		boolean aValid = false;
		boolean bValid = false;
		try {
			Long.parseLong(a);
			aValid = true;
		} catch (Exception e) {
		}
		try {
			Long.parseLong(b);
			bValid = true;
		} catch (Exception e) {
		}
		if (bValid && aValid) {
			return value;
		} else if (op == null) {
			return "Unknown";
		} else if (bValid) {
			return "Unknown" + op + b;
		} else if (aValid) {
			return a + op + "Unknown";
		} else {
			return "Unknown";
		}
	}

	private String parseFloatOrUnknown(float v, String a, String b, String op) {
		String value = v + "";
		boolean aValid = false;
		boolean bValid = false;
		try {
			Float.parseFloat(a);
			aValid = true;
		} catch (Exception e) {
		}
		try {
			Float.parseFloat(b);
			bValid = true;
		} catch (Exception e) {
		}
		if (bValid && aValid) {
			return value;
		} else if (op == null) {
			return "Unknown";
		} else if (bValid) {
			return "Unknown" + op + b;
		} else if (aValid) {
			return a + op + "Unknown";
		} else {
			return "Unknown";
		}
	}

	private String parseDoubleOrUnknown(double v, String a, String b, String op) {
		String value = v + "";
		boolean aValid = false;
		boolean bValid = false;
		try {
			Double.parseDouble(a);
			aValid = true;
		} catch (Exception e) {
		}
		try {
			Double.parseDouble(b);
			bValid = true;
		} catch (Exception e) {
		}
		if (bValid && aValid) {
			return value;
		} else if (op == null) {
			return "Unknown";
		} else if (bValid) {
			return "Unknown" + op + b;
		} else if (aValid) {
			return a + op + "Unknown";
		} else {
			return "Unknown";
		}
	}

	private int parseInt(String s) {
		try {
			return Integer.parseInt(s);
		} catch (Exception e) {
			return 1;
		}
	}

	private long parseLong(String s) {
		try {
			return Long.parseLong(s);
		} catch (Exception e) {
			return 1;
		}
	}

	private float parseFloat(String s) {
		try {
			return Float.parseFloat(s);
		} catch (Exception e) {
			return 1;
		}
	}

	private double parseDouble(String s) {
		try {
			return Double.parseDouble(s);
		} catch (Exception e) {
			return 1;
		}
	}

	private void handleReturn(StackAlter alter) {
		AbstractInsnNode node = alter.node;

		switch (node.getOpcode()) {
			case ARETURN:
			case DRETURN:
			case FRETURN:
			case LRETURN:
			case IRETURN:
			case RETURN:
				state = State.FINISHED;
		}
	}

	private void handleStore(StackAlter alter) {
		AbstractInsnNode node = alter.node;

		switch (node.getOpcode()) {
			case ASTORE:
			case DSTORE:
			case FSTORE:
			case LSTORE:
			case ISTORE:
				locals.set(((VarInsnNode) node).var, popped.get(0), instruction);
		}
	}

	private void handleLoad(StackAlter alter) {
		AbstractInsnNode node = alter.node;

		switch (node.getOpcode()) {
			case ALOAD:
			case DLOAD:
			case FLOAD:
			case LLOAD:
			case ILOAD: {
				EntryValue value = stack.pop(locals.entries[((VarInsnNode) node).var].getType());
				EntryValue parent = locals.get(((VarInsnNode) node).var, value.getType());
				stack.push(parent.move(value.getType(), value.getSourceHistory().viewHistory().getLast()));
				break;
			}
			case IINC: {
				EntryValue parent = locals.get(((IincInsnNode) node).var, EntryType.INT);
				if (isInt(parent.getKnownValue())) {
					locals.entries[((IincInsnNode) node).var] = new EntryValue(EntryType.INT, instruction, List.of(parent), //
							"" + (parseInt(parent.getKnownValue()) + ((IincInsnNode) node).incr));
				} else {
					locals.entries[((IincInsnNode) node).var] = new EntryValue(EntryType.INT, instruction, List.of(parent));
				}
				break;
			}

		}
	}

	private void handleFrame(StackAlter alter) {
		if (alter.node instanceof FrameNode frame) {
			switch (frame.type) {
				case F_NEW:
				case F_FULL:
					int offset = 0;
					for (Object o : frame.local) {
						if (o instanceof Integer i) {
							switch (i) {
								case 1:
									locals.get(offset, EntryType.INT);
									offset++;
									break;
								case 2:
									locals.get(offset, EntryType.FLOAT);
									offset++;
									break;
								case 3:
									locals.get(offset, EntryType.DOUBLE);
									offset += 2;
									break;
								case 4:
									locals.get(offset, EntryType.LONG);
									offset += 2;
									break;
								default:
									throw new UnsupportedOperationException("Unexpected local type " + i);
							}
						} else if (o instanceof String) {
							locals.get(offset, EntryType.REFERENCE);
							offset++;
						} else {
							throw new UnsupportedOperationException("Unexpected frame local type " + o);
						}
					}
					for (int i = offset; i < locals.count; i++) {
						locals.set(i, new EntryValue(null), instruction);
					}

					stack.entries.clear();
					for (Object o : frame.stack) {
						if (o instanceof Integer i) {
							switch (i) {
								case 1:
									stack.entries.add(new EntryValue(EntryType.INT, instruction));
									break;
								case 2:
									stack.entries.add(new EntryValue(EntryType.FLOAT, instruction));
									break;
								case 3:
									stack.entries.add(new EntryValue(EntryType.DOUBLE, instruction));
									stack.entries.add(new EntryValue(EntryType.DOUBLE_2ND, instruction));
									break;
								case 4:
									stack.entries.add(new EntryValue(EntryType.LONG, instruction));
									stack.entries.add(new EntryValue(EntryType.LONG_2ND, instruction));
									break;
								default:
									throw new UnsupportedOperationException("Unexpected stack type " + i);
							}
						} else if (o instanceof String) {
							stack.entries.add(new EntryValue(EntryType.REFERENCE, instruction));
						} else {
							throw new UnsupportedOperationException("Unexpected frame stack type " + o);
						}

					}

					break;
				case F_APPEND:
				case F_CHOP:
				case F_SAME:
				case F_SAME1:
				default:
					throw new UnsupportedOperationException("Unsupported frame type " + frame.type);
			}
		}
	}

	public List<Label> getJumps() {
		StackAlter alter = insts.alters.get(instruction);
		if (alter.add.contains(StackOp.THROW)) {
			List<Label> throwTargets = new ArrayList<>();
			var catches = getActiveCatches();
			for (var tryCatch : catches) {
				throwTargets.add(tryCatch.handler.getLabel());
			}
			return throwTargets;
		}
		if (alter.add.contains(StackOp.RET)) {
			VarInsnNode node = (VarInsnNode) alter.node;
			EntryValue value = locals.get(node.var, EntryType.RETURN_ADDRESS);
			var history = value.getSourceHistory().viewHistory();
			int index = history.get(history.size() - 1);
			StackAlter labelAlter = insts.alters.get(index - 1);
			return List.of(((LabelNode) labelAlter.node).getLabel());

		}
		return alter.jumpTargets;
	}

	public void purgeHistory() {
		for (int i = 0; i < stack.entries.size(); i++) {
			var entry = stack.entries.get(i);
			var history = entry.getSourceHistory();
			if (history.viewHistory().size() > 0 || !history.viewSources().isEmpty()) {
				stack.entries.set(i, new EntryValue(entry.getType(), history.viewHistory().getLast(), entry.getKnownValue()));
			}
		}

		for (int i = 0; i < locals.entries.length; i++) {
			var entry = locals.entries[i];
			var history = entry.getSourceHistory();
			if (history.viewHistory().size() > 0 || !history.viewSources().isEmpty()) {
				locals.entries[i] = new EntryValue(entry.getType(), history.viewHistory().getLast(), entry.getKnownValue());
			}
		}

	}

	public Optional<Label> getExpectedJumpTarget() {
		ensureState(State.JUMP);
		var inst = getInstruction();
		var node = inst.node;
		if (inst.jumpTargets.isEmpty()) {
			return Optional.empty();
		}
		switch (node.getOpcode()) {
			case IFEQ -> {
				if (isInt(popped.get(0).getKnownValue())) {
					int v = parseInt(popped.get(0).getKnownValue());
					if (v == 0) {
						return Optional.of(inst.jumpTargets.get(0));
					} else {
						return Optional.empty();
					}
				}
			}
			case IFNE -> {
				if (isInt(popped.get(0).getKnownValue())) {
					int v = parseInt(popped.get(0).getKnownValue());
					if (v != 0) {
						return Optional.of(inst.jumpTargets.get(0));
					} else {
						return Optional.empty();
					}
				}
			}
			case IFLT -> {
				if (isInt(popped.get(0).getKnownValue())) {
					int v = parseInt(popped.get(0).getKnownValue());
					if (v < 0) {
						return Optional.of(inst.jumpTargets.get(0));
					} else {
						return Optional.empty();
					}
				}
			}
			case IFGE -> {
				if (isInt(popped.get(0).getKnownValue())) {
					int v = parseInt(popped.get(0).getKnownValue());
					if (v >= 0) {
						return Optional.of(inst.jumpTargets.get(0));
					} else {
						return Optional.empty();
					}
				}
			}
			case IFGT -> {
				if (isInt(popped.get(0).getKnownValue())) {
					int v = parseInt(popped.get(0).getKnownValue());
					if (v > 0) {
						return Optional.of(inst.jumpTargets.get(0));
					} else {
						return Optional.empty();
					}
				}
			}
			case IFLE -> {
				if (isInt(popped.get(0).getKnownValue())) {
					int v = parseInt(popped.get(0).getKnownValue());
					if (v <= 0) {
						return Optional.of(inst.jumpTargets.get(0));
					} else {
						return Optional.empty();
					}
				}
			}
			case IF_ICMPEQ -> {
				if (isInt(popped.get(0).getKnownValue()) && isInt(popped.get(1).getKnownValue())) {
					int v2 = parseInt(popped.get(0).getKnownValue());
					int v1 = parseInt(popped.get(1).getKnownValue());
					if (v1 == v2) {
						return Optional.of(inst.jumpTargets.get(0));
					} else {
						return Optional.empty();
					}
				}
			}
			case IF_ICMPNE -> {
				if (isInt(popped.get(0).getKnownValue()) && isInt(popped.get(1).getKnownValue())) {
					int v2 = parseInt(popped.get(0).getKnownValue());
					int v1 = parseInt(popped.get(1).getKnownValue());
					if (v1 != v2) {
						return Optional.of(inst.jumpTargets.get(0));
					} else {
						return Optional.empty();
					}
				}
			}
			case IF_ICMPLT -> {
				if (isInt(popped.get(0).getKnownValue()) && isInt(popped.get(1).getKnownValue())) {
					int v2 = parseInt(popped.get(0).getKnownValue());
					int v1 = parseInt(popped.get(1).getKnownValue());
					if (v1 < v2) {
						return Optional.of(inst.jumpTargets.get(0));
					} else {
						return Optional.empty();
					}
				}
			}
			case IF_ICMPGE -> {
				if (isInt(popped.get(0).getKnownValue()) && isInt(popped.get(1).getKnownValue())) {
					int v2 = parseInt(popped.get(0).getKnownValue());
					int v1 = parseInt(popped.get(1).getKnownValue());
					if (v1 >= v2) {
						return Optional.of(inst.jumpTargets.get(0));
					} else {
						return Optional.empty();
					}
				}
			}
			case IF_ICMPGT -> {
				if (isInt(popped.get(0).getKnownValue()) && isInt(popped.get(1).getKnownValue())) {
					int v2 = parseInt(popped.get(0).getKnownValue());
					int v1 = parseInt(popped.get(1).getKnownValue());
					if (v1 > v2) {
						return Optional.of(inst.jumpTargets.get(0));
					} else {
						return Optional.empty();
					}
				}
			}
			case IF_ICMPLE -> {
				if (isInt(popped.get(0).getKnownValue()) && isInt(popped.get(1).getKnownValue())) {
					int v2 = parseInt(popped.get(0).getKnownValue());
					int v1 = parseInt(popped.get(1).getKnownValue());
					if (v1 <= v2) {
						return Optional.of(inst.jumpTargets.get(0));
					} else {
						return Optional.empty();
					}
				}
			}
			case IF_ACMPEQ -> {
				String known = popped.get(0).getKnownValue();
				if (known.endsWith("Type: null")) {
					String known2 = popped.get(1).getKnownValue();
					if (known2.endsWith("Type: null")) {
						return Optional.of(inst.jumpTargets.get(0));
					}
				}
			}
			case IF_ACMPNE -> {
				String known = popped.get(0).getKnownValue();
				if (known.endsWith("Type: null")) {
					String known2 = popped.get(1).getKnownValue();
					if (known2.endsWith("Type: null")) {
						return Optional.empty();
					}
				}
			}
			case GOTO -> {
				return Optional.of(inst.jumpTargets.get(0));
			}
			case JSR -> {
				return Optional.of(inst.jumpTargets.get(0));
			}
			case IFNULL -> {
				String known = popped.get(0).getKnownValue();
				if (known.endsWith("Type: null")) {
					return Optional.of(inst.jumpTargets.get(0));
				}
			}
			case IFNONNULL -> {
				String known = popped.get(0).getKnownValue();
				if (known.endsWith("Type: null")) {
					return Optional.empty();
				}
			}
			case TABLESWITCH -> {
				var sw = (TableSwitchInsnNode) node;
				String known = popped.get(0).getKnownValue();
				if (isInt(known)) {
					int value = parseInt(known);
					if (value >= sw.min && value <= sw.max) {
						return Optional.of(sw.labels.get(value - sw.min).getLabel());
					} else {
						return Optional.of(sw.dflt.getLabel());
					}
				}
			}
			case LOOKUPSWITCH -> {
				var sw = (LookupSwitchInsnNode) node;
				String known = popped.get(0).getKnownValue();
				if (isInt(known)) {
					int value = parseInt(known);
					if (sw.keys.contains(value)) {
						return Optional.of(sw.labels.get(sw.keys.indexOf(value)).getLabel());
					} else {
						return Optional.of(sw.dflt.getLabel());
					}
				}
			}
		}
		return null;
	}

	public void performJump(Label l) {
		try {
			if (l == null) {
				instruction++;
				if (instruction >= insts.alters.size()) {
					state = State.FINISHED;
				} else {
					state = State.POP;
				}
				return;
			}
			for (int i = 0; i < insts.alters.size(); i++) {
				StackAlter alter = insts.alters.get(i);
				if (alter.node instanceof LabelNode node) {
					if (node.getLabel().equals(l)) {
						instruction = i;
						state = State.POP;
						return;
					}
				}
			}
			throw new IllegalStateException("Invalid jump label.");
		} catch (Exception e) {
			e.printStackTrace();
			state = State.ERRORED;
			errorMessage = e.getMessage();
		}
	}

	public String currentInstruction() {
		return insts.toString(insts.alters.get(instruction).node);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (StackAlter alter : insts.alters) {
			sb.append(insts.toString(alter.node));
			sb.append("\n");
		}
		return sb.toString();
	}

	public StackAlter getInstruction() {
		return insts.alters.get(instruction);
	}

}
