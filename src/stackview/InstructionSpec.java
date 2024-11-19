package stackview;

import static org.objectweb.asm.Opcodes.*;
import static stackview.StackAlter.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
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

public class InstructionSpec extends MethodVisitor {

	public List<StackAlter> alters = new ArrayList<>();
	public int locals;
	public int stackSize;
	public Map<Label, Integer> lineNumbers = new HashMap<>();
	public Map<Label, String> labelNames = new HashMap<>();
	int labelIndex;
	public List<LocalVariableNode> localsDebug = new ArrayList<>();
	public List<TryCatchBlockNode> tryCatchBlocks = new ArrayList<>();
	String descriptor;
	boolean staticMethod;

	InstructionString stringer = new InstructionString(this);

	public InstructionSpec(String descriptor, boolean staticMethod) {
		super(ASM9);
		this.descriptor = descriptor;
		this.staticMethod = staticMethod;
	}

	public String toString(AbstractInsnNode node) {
		return stringer.toString(node);
	}

	public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
		super.visitFieldInsn(opcode, owner, name, descriptor);
		StackAlter alter = alter(new FieldInsnNode(opcode, owner, name, descriptor));
		alters.add(alter);
		switch (opcode) {
			case GETSTATIC -> {
				alter.add(Stack.from(descriptor));
			}
			case PUTSTATIC -> {
				alter.remove(Stack.from(descriptor));
			}
			case GETFIELD -> {
				alter.remove(StackOp.REFERENCE).add(Stack.from(descriptor));
			}
			case PUTFIELD -> {
				alter.remove(StackOp.REFERENCE).remove(Stack.from(descriptor));
			}
			default -> {
				throw new UnsupportedOperationException("Unknown opcode " + opcode);
			}
		}
	}

	public void visitIincInsn(int varIndex, int increment) {
		super.visitIincInsn(varIndex, increment);

		StackAlter alter = alter(new IincInsnNode(varIndex, increment));
		alters.add(alter);
	}

	public void visitInsn(int opcode) {
		super.visitInsn(opcode);
		StackAlter alter = alter(new InsnNode(opcode));
		alters.add(alter);
		switch (opcode) {
			case NOP -> {
			}
			case ACONST_NULL -> {
				alter.add(StackOp.REFERENCE);
			}
			case ICONST_M1 -> {
				alter.add(StackOp.INT);
			}
			case ICONST_0 -> {
				alter.add(StackOp.INT);
			}
			case ICONST_1 -> {
				alter.add(StackOp.INT);
			}
			case ICONST_2 -> {
				alter.add(StackOp.INT);
			}
			case ICONST_3 -> {
				alter.add(StackOp.INT);
			}
			case ICONST_4 -> {
				alter.add(StackOp.INT);
			}
			case ICONST_5 -> {
				alter.add(StackOp.INT);
			}
			case LCONST_0 -> {
				alter.add(StackOp.LONG);
			}
			case LCONST_1 -> {
				alter.add(StackOp.LONG);
			}
			case FCONST_0 -> {
				alter.add(StackOp.FLOAT);
			}
			case FCONST_1 -> {
				alter.add(StackOp.FLOAT);
			}
			case FCONST_2 -> {
				alter.add(StackOp.FLOAT);
			}
			case DCONST_0 -> {
				alter.add(StackOp.DOUBLE);
			}
			case DCONST_1 -> {
				alter.add(StackOp.DOUBLE);
			}
			case IALOAD -> {
				alter.remove(StackOp.INT, StackOp.REFERENCE).add(StackOp.INT);
			}
			case LALOAD -> {
				alter.remove(StackOp.INT, StackOp.REFERENCE).add(StackOp.LONG);
			}
			case FALOAD -> {
				alter.remove(StackOp.INT, StackOp.REFERENCE).add(StackOp.FLOAT);
			}
			case DALOAD -> {
				alter.remove(StackOp.INT, StackOp.REFERENCE).add(StackOp.DOUBLE);
			}
			case AALOAD -> {
				alter.remove(StackOp.INT, StackOp.REFERENCE).add(StackOp.REFERENCE);
			}
			case BALOAD -> {
				alter.remove(StackOp.INT, StackOp.REFERENCE).add(StackOp.INT);
			}
			case CALOAD -> {
				alter.remove(StackOp.INT, StackOp.REFERENCE).add(StackOp.INT);
			}
			case SALOAD -> {
				alter.remove(StackOp.INT, StackOp.REFERENCE).add(StackOp.INT);
			}
			case IASTORE -> {
				alter.remove(StackOp.INT, StackOp.INT, StackOp.REFERENCE);
			}
			case LASTORE -> {
				alter.remove(StackOp.LONG, StackOp.INT, StackOp.REFERENCE);
			}
			case FASTORE -> {
				alter.remove(StackOp.FLOAT, StackOp.INT, StackOp.REFERENCE);
			}
			case DASTORE -> {
				alter.remove(StackOp.DOUBLE, StackOp.INT, StackOp.REFERENCE);
			}
			case AASTORE -> {
				alter.remove(StackOp.REFERENCE, StackOp.INT, StackOp.REFERENCE);
			}
			case BASTORE -> {
				alter.remove(StackOp.INT, StackOp.INT, StackOp.REFERENCE);
			}
			case CASTORE -> {
				alter.remove(StackOp.INT, StackOp.INT, StackOp.REFERENCE);
			}
			case SASTORE -> {
				alter.remove(StackOp.INT, StackOp.INT, StackOp.REFERENCE);
			}
			case POP -> {
				alter.remove(StackOp.ANY_1);
			}
			case POP2 -> {
				alter.remove(StackOp.ANY_2);
			}
			case DUP -> {
				alter.remove(StackOp.ANY_1).add(StackOp.ANY_1, StackOp.ANY_1);
			}
			case DUP_X1 -> {
				alter.remove(StackOp.ANY_1, StackOp.ANY_1).add(StackOp.ANY_1, StackOp.ANY_1, StackOp.ANY_1);
			}
			case DUP_X2 -> {
				alter.remove(StackOp.ANY_1, StackOp.ANY_2).add(StackOp.ANY_1, StackOp.ANY_2, StackOp.ANY_1);
			}
			case DUP2 -> {
				alter.remove(StackOp.ANY_2).add(StackOp.ANY_2, StackOp.ANY_2);
			}
			case DUP2_X1 -> {
				alter.remove(StackOp.ANY_2, StackOp.ANY_1).add(StackOp.ANY_2, StackOp.ANY_1, StackOp.ANY_2);
			}
			case DUP2_X2 -> {
				alter.remove(StackOp.ANY_2, StackOp.ANY_2).add(StackOp.ANY_2, StackOp.ANY_2, StackOp.ANY_2);
			}
			case SWAP -> {
				alter.remove(StackOp.ANY_1, StackOp.ANY_1).add(StackOp.ANY_1, StackOp.ANY_1);
			}
			case IADD -> {
				alter.remove(StackOp.INT, StackOp.INT).add(StackOp.INT);
			}
			case LADD -> {
				alter.remove(StackOp.LONG, StackOp.LONG).add(StackOp.LONG);
			}
			case FADD -> {
				alter.remove(StackOp.FLOAT, StackOp.FLOAT).add(StackOp.FLOAT);
			}
			case DADD -> {
				alter.remove(StackOp.DOUBLE, StackOp.DOUBLE).add(StackOp.DOUBLE);
			}
			case ISUB -> {
				alter.remove(StackOp.INT, StackOp.INT).add(StackOp.INT);
			}
			case LSUB -> {
				alter.remove(StackOp.LONG, StackOp.LONG).add(StackOp.LONG);
			}
			case FSUB -> {
				alter.remove(StackOp.FLOAT, StackOp.FLOAT).add(StackOp.FLOAT);
			}
			case DSUB -> {
				alter.remove(StackOp.DOUBLE, StackOp.DOUBLE).add(StackOp.DOUBLE);
			}
			case IMUL -> {
				alter.remove(StackOp.INT, StackOp.INT).add(StackOp.INT);
			}
			case LMUL -> {
				alter.remove(StackOp.LONG, StackOp.LONG).add(StackOp.LONG);
			}
			case FMUL -> {
				alter.remove(StackOp.FLOAT, StackOp.FLOAT).add(StackOp.FLOAT);
			}
			case DMUL -> {
				alter.remove(StackOp.DOUBLE, StackOp.DOUBLE).add(StackOp.DOUBLE);
			}
			case IDIV -> {
				alter.remove(StackOp.INT, StackOp.INT).add(StackOp.INT);
			}
			case LDIV -> {
				alter.remove(StackOp.LONG, StackOp.LONG).add(StackOp.LONG);
			}
			case FDIV -> {
				alter.remove(StackOp.FLOAT, StackOp.FLOAT).add(StackOp.FLOAT);
			}
			case DDIV -> {
				alter.remove(StackOp.DOUBLE, StackOp.DOUBLE).add(StackOp.DOUBLE);
			}
			case IREM -> {
				alter.remove(StackOp.INT, StackOp.INT).add(StackOp.INT);
			}
			case LREM -> {
				alter.remove(StackOp.LONG, StackOp.LONG).add(StackOp.LONG);
			}
			case FREM -> {
				alter.remove(StackOp.FLOAT, StackOp.FLOAT).add(StackOp.FLOAT);
			}
			case DREM -> {
				alter.remove(StackOp.DOUBLE, StackOp.DOUBLE).add(StackOp.DOUBLE);
			}
			case INEG -> {
				alter.remove(StackOp.INT).add(StackOp.INT);
			}
			case LNEG -> {
				alter.remove(StackOp.LONG).add(StackOp.LONG);
			}
			case FNEG -> {
				alter.remove(StackOp.FLOAT).add(StackOp.FLOAT);
			}
			case DNEG -> {
				alter.remove(StackOp.DOUBLE).add(StackOp.DOUBLE);
			}
			case ISHL -> {
				alter.remove(StackOp.INT, StackOp.INT).add(StackOp.INT);
			}
			case LSHL -> {
				alter.remove(StackOp.INT, StackOp.LONG).add(StackOp.LONG);
			}
			case ISHR -> {
				alter.remove(StackOp.INT, StackOp.INT).add(StackOp.INT);
			}
			case LSHR -> {
				alter.remove(StackOp.INT, StackOp.LONG).add(StackOp.LONG);
			}
			case IUSHR -> {
				alter.remove(StackOp.INT, StackOp.INT).add(StackOp.INT);
			}
			case LUSHR -> {
				alter.remove(StackOp.INT, StackOp.LONG).add(StackOp.LONG);
			}
			case IAND -> {
				alter.remove(StackOp.INT, StackOp.INT).add(StackOp.INT);
			}
			case LAND -> {
				alter.remove(StackOp.LONG, StackOp.LONG).add(StackOp.LONG);
			}
			case IOR -> {
				alter.remove(StackOp.INT, StackOp.INT).add(StackOp.INT);
			}
			case LOR -> {
				alter.remove(StackOp.LONG, StackOp.LONG).add(StackOp.LONG);
			}
			case IXOR -> {
				alter.remove(StackOp.INT, StackOp.INT).add(StackOp.INT);
			}
			case LXOR -> {
				alter.remove(StackOp.LONG, StackOp.LONG).add(StackOp.LONG);
			}
			case I2L -> {
				alter.remove(StackOp.INT).add(StackOp.LONG);
			}
			case I2F -> {
				alter.remove(StackOp.INT).add(StackOp.FLOAT);
			}
			case I2D -> {
				alter.remove(StackOp.INT).add(StackOp.DOUBLE);
			}
			case L2I -> {
				alter.remove(StackOp.LONG).add(StackOp.INT);
			}
			case L2F -> {
				alter.remove(StackOp.LONG).add(StackOp.FLOAT);
			}
			case L2D -> {
				alter.remove(StackOp.LONG).add(StackOp.DOUBLE);
			}
			case F2I -> {
				alter.remove(StackOp.FLOAT).add(StackOp.INT);
			}
			case F2L -> {
				alter.remove(StackOp.FLOAT).add(StackOp.LONG);
			}
			case F2D -> {
				alter.remove(StackOp.FLOAT).add(StackOp.DOUBLE);
			}
			case D2I -> {
				alter.remove(StackOp.DOUBLE).add(StackOp.INT);
			}
			case D2L -> {
				alter.remove(StackOp.DOUBLE).add(StackOp.LONG);
			}
			case D2F -> {
				alter.remove(StackOp.DOUBLE).add(StackOp.FLOAT);
			}
			case I2B -> {
				alter.remove(StackOp.INT).add(StackOp.INT);
			}
			case I2C -> {
				alter.remove(StackOp.INT).add(StackOp.INT);
			}
			case I2S -> {
				alter.remove(StackOp.INT).add(StackOp.INT);
			}
			case LCMP -> {
				alter.remove(StackOp.LONG, StackOp.LONG).add(StackOp.INT);
			}
			case FCMPL -> {
				alter.remove(StackOp.FLOAT, StackOp.FLOAT).add(StackOp.INT);
			}
			case FCMPG -> {
				alter.remove(StackOp.FLOAT, StackOp.FLOAT).add(StackOp.INT);
			}
			case DCMPL -> {
				alter.remove(StackOp.DOUBLE, StackOp.DOUBLE).add(StackOp.INT);
			}
			case DCMPG -> {
				alter.remove(StackOp.DOUBLE, StackOp.DOUBLE).add(StackOp.INT);
			}
			case IRETURN -> {
				alter.remove(StackOp.INT).add(StackOp.CLEAR);
			}
			case LRETURN -> {
				alter.remove(StackOp.LONG).add(StackOp.CLEAR);
			}
			case FRETURN -> {
				alter.remove(StackOp.FLOAT).add(StackOp.CLEAR);
			}
			case DRETURN -> {
				alter.remove(StackOp.DOUBLE).add(StackOp.CLEAR);
			}
			case ARETURN -> {
				alter.remove(StackOp.REFERENCE).add(StackOp.CLEAR);
			}
			case RETURN -> {
				alter.add(StackOp.CLEAR);
			}
			case ARRAYLENGTH -> {
				alter.remove(StackOp.REFERENCE).add(StackOp.INT);
			}
			case ATHROW -> {
				alter.remove(StackOp.REFERENCE).add(StackOp.CLEAR).add(StackOp.REFERENCE).add(StackOp.THROW);
			}
			case MONITORENTER -> {
				alter.remove(StackOp.REFERENCE);
			}
			case MONITOREXIT -> {
				alter.remove(StackOp.REFERENCE);
			}
			default -> {
				throw new UnsupportedOperationException("Unknown opcode " + opcode);
			}
		}
	}

	public void visitIntInsn(int opcode, int operand) {
		super.visitIntInsn(opcode, operand);
		StackAlter alter = alter(new IntInsnNode(opcode, operand));
		alters.add(alter);
		switch (opcode) {
			case BIPUSH -> {
				alter.add(StackOp.INT);
			}
			case SIPUSH -> {
				alter.add(StackOp.INT);
			}
			case NEWARRAY -> {
				alter.remove(StackOp.INT).add(StackOp.REFERENCE);
			}
			default -> {
				throw new UnsupportedOperationException("Unknown opcode " + opcode);
			}
		}
	}

	public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
		super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
		StackAlter alter = alter(new InvokeDynamicInsnNode(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments));
		alters.add(alter);
		alter.remove(Stack.fromMethodArgumentsPop(descriptor));
		alter.add(Stack.fromMethodReturn(descriptor));

	}

	public void visitJumpInsn(int opcode, Label label) {
		super.visitJumpInsn(opcode, label);
		if (opcode == JSR) {
			Label extra = new Label();
			StackAlter alter = alter(new LabelNode(extra));
			alters.add(alter);
			labelNames.put(extra, "L" + labelIndex++);
		}
		StackAlter alter = alter(new JumpInsnNode(opcode, new LabelNode(label)));
		alters.add(alter);
		alter.jumpTargets.add(label);
		switch (opcode) {
			case IFEQ -> {
				alter.remove(StackOp.INT);
			}
			case IFNE -> {
				alter.remove(StackOp.INT);
			}
			case IFLT -> {
				alter.remove(StackOp.INT);
			}
			case IFGE -> {
				alter.remove(StackOp.INT);
			}
			case IFGT -> {
				alter.remove(StackOp.INT);
			}
			case IFLE -> {
				alter.remove(StackOp.INT);
			}
			case IF_ICMPEQ -> {
				alter.remove(StackOp.INT, StackOp.INT);
			}
			case IF_ICMPNE -> {
				alter.remove(StackOp.INT, StackOp.INT);
			}
			case IF_ICMPLT -> {
				alter.remove(StackOp.INT, StackOp.INT);
			}
			case IF_ICMPGE -> {
				alter.remove(StackOp.INT, StackOp.INT);
			}
			case IF_ICMPGT -> {
				alter.remove(StackOp.INT, StackOp.INT);
			}
			case IF_ICMPLE -> {
				alter.remove(StackOp.INT, StackOp.INT);
			}
			case IF_ACMPEQ -> {
				alter.remove(StackOp.REFERENCE, StackOp.REFERENCE);
			}
			case IF_ACMPNE -> {
				alter.remove(StackOp.REFERENCE, StackOp.REFERENCE);
			}
			case GOTO -> {
			}
			case JSR -> {
				alter.add(StackOp.RETURN_ADDRESS);
			}
			case IFNULL -> {
				alter.remove(StackOp.REFERENCE);
			}
			case IFNONNULL -> {
				alter.remove(StackOp.REFERENCE);
			}
			default -> {
				throw new UnsupportedOperationException("Unknown opcode " + opcode);
			}
		}
		alter.add(StackOp.JUMP);
	}

	public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
		super.visitFrame(type, numLocal, local, numStack, stack);
		StackAlter alter = alter(new FrameNode(type, numLocal, local, numStack, stack));
		alters.add(alter);
		alter.add(StackOp.FRAME);

		switch (type) {
			case F_NEW:
			case F_FULL:
				break;
			case F_APPEND:
			case F_CHOP:
			case F_SAME:
			case F_SAME1:
			default:
				throw new UnsupportedOperationException("Unsupported frame type " + type);
		}
	}

	public void visitLdcInsn(Object cst) {
		super.visitLdcInsn(cst);
		StackAlter alter = alter(new LdcInsnNode(cst));
		alters.add(alter);
		if (cst instanceof Integer) {
			alter.add(StackOp.INT);
		} else if (cst instanceof Float) {
			alter.add(StackOp.FLOAT);
		} else if (cst instanceof Long) {
			alter.add(StackOp.LONG);
		} else if (cst instanceof Double) {
			alter.add(StackOp.DOUBLE);
		} else if (cst instanceof String) {
			alter.add(StackOp.REFERENCE);
		} else if (cst instanceof Type v) {
			int sort = v.getSort();
			if (sort == Type.OBJECT) {
				alter.add(StackOp.REFERENCE);
			} else if (sort == Type.ARRAY) {
				alter.add(StackOp.REFERENCE);
			} else if (sort == Type.METHOD) {
				alter.add(Stack.fromMethodReturn(v));
			} else {
				throw new UnsupportedOperationException("Unknown type " + cst + " " + v);
			}
		} else if (cst instanceof Handle v) {
			Type t = Type.getType(v.getDesc());
			if (t.getSort() == Type.METHOD) {
				alter.add(Stack.fromMethodReturn(t));
			} else {
				alter.add(Stack.from(t));
			}
		} else if (cst instanceof ConstantDynamic v) {
			alter.add(Stack.from(v.getDescriptor()));
		} else {
			throw new UnsupportedOperationException("Unknown type " + cst);
		}
	}

	public void visitLabel(Label label) {
		super.visitLabel(label);
		StackAlter alter = alter(new LabelNode(label));
		alters.add(alter);
		labelNames.put(label, "L" + labelIndex++);
	}

	public void visitLineNumber(int line, Label start) {
		super.visitLineNumber(line, start);
		lineNumbers.put(start, line);
	}

	public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
		super.visitLocalVariable(name, descriptor, signature, start, end, index);
		localsDebug.add(new LocalVariableNode(name, descriptor, signature, new LabelNode(start), new LabelNode(end), index));
	}

	public void visitMaxs(int maxStack, int maxLocals) {
		super.visitMaxs(maxStack, maxLocals);
		stackSize = maxStack;
		locals = maxLocals;
	}

	public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
		super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
		StackAlter alter = alter(new MethodInsnNode(opcode, owner, name, descriptor, isInterface));
		alters.add(alter);
		alter.remove(Stack.fromMethodArgumentsPop(descriptor));

		switch (opcode) {
			case INVOKEVIRTUAL -> {
				alter.remove(StackOp.REFERENCE);
			}
			case INVOKESPECIAL -> {
				alter.remove(StackOp.REFERENCE);
			}
			case INVOKESTATIC -> {
			}
			case INVOKEINTERFACE -> {
				alter.remove(StackOp.REFERENCE);
			}
			default -> {
				throw new UnsupportedOperationException("Unknown opcode " + opcode);
			}
		}
		if (Type.getReturnType(descriptor).getSort() != Type.VOID) {
			alter.add(Stack.fromMethodReturn(descriptor));
		}
	}

	public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
		super.visitLookupSwitchInsn(dflt, keys, labels);
		LabelNode[] nodes = new LabelNode[labels.length];
		for (int i = 0; i < labels.length; i++) {
			nodes[i] = new LabelNode(labels[i]);
		}
		StackAlter alter = alter(new LookupSwitchInsnNode(new LabelNode(dflt), keys, nodes));
		alters.add(alter);

		for (int i = 0; i < labels.length; i++) {
			alter.jumpTargets.add(labels[i]);
		}
		alter.jumpTargets.add(dflt);

		alter.remove(StackOp.INT);
		alter.add(StackOp.JUMP);

	}

	public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
		super.visitMultiANewArrayInsn(descriptor, numDimensions);
		StackAlter alter = alter(new MultiANewArrayInsnNode(descriptor, numDimensions));
		alters.add(alter);
		for (int i = 0; i < numDimensions; i++) {
			alter.remove(StackOp.INT);
		}
		alter.add(StackOp.REFERENCE);
	}

	public void visitTypeInsn(int opcode, String type) {
		super.visitTypeInsn(opcode, type);
		StackAlter alter = alter(new TypeInsnNode(opcode, type));
		alters.add(alter);
		switch (opcode) {
			case NEW -> {
				alter.add(StackOp.REFERENCE);
			}
			case ANEWARRAY -> {
				alter.remove(StackOp.INT).add(StackOp.REFERENCE);
			}
			case CHECKCAST -> {
				alter.remove(StackOp.REFERENCE).add(StackOp.REFERENCE);
			}
			case INSTANCEOF -> {
				alter.remove(StackOp.REFERENCE).add(StackOp.INT);
			}
			default -> {
				throw new UnsupportedOperationException("Unknown opcode " + opcode);
			}
		}
	}

	public void visitVarInsn(int opcode, int varIndex) {
		super.visitVarInsn(opcode, varIndex);
		StackAlter alter = alter(new VarInsnNode(opcode, varIndex));
		alters.add(alter);
		switch (opcode) {
			case ILOAD -> {
				alter.add(StackOp.INT);
			}
			case LLOAD -> {
				alter.add(StackOp.LONG);
			}
			case FLOAD -> {
				alter.add(StackOp.FLOAT);
			}
			case DLOAD -> {
				alter.add(StackOp.DOUBLE);
			}
			case ALOAD -> {
				alter.add(StackOp.REFERENCE);
			}
			case ISTORE -> {
				alter.remove(StackOp.INT);
			}
			case LSTORE -> {
				alter.remove(StackOp.LONG);
			}
			case FSTORE -> {
				alter.remove(StackOp.FLOAT);
			}
			case DSTORE -> {
				alter.remove(StackOp.DOUBLE);
			}
			case ASTORE -> {
				alter.remove(StackOp.REFERENCE_OR_RETURN_ADDRESS);
			}
			case RET -> {
				alter.add(StackOp.RET);
			}
			default -> {
				throw new UnsupportedOperationException("Unknown opcode " + opcode);
			}
		}

	}

	public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
		super.visitTableSwitchInsn(min, max, dflt, labels);
		LabelNode[] nodes = new LabelNode[labels.length];
		for (int i = 0; i < labels.length; i++) {
			nodes[i] = new LabelNode(labels[i]);
		}
		StackAlter alter = alter(new TableSwitchInsnNode(min, max, new LabelNode(dflt), nodes));
		alters.add(alter);
		alter.remove(StackOp.INT);
		alter.add(StackOp.JUMP);
		alter.jumpTargets.add(dflt);
		for (int i = 0; i < labels.length; i++) {
			alter.jumpTargets.add(labels[i]);
		}
	}

	public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
		super.visitTryCatchBlock(start, end, handler, type);
		tryCatchBlocks.add(new TryCatchBlockNode(new LabelNode(start), new LabelNode(end), new LabelNode(handler), type));
	}
}
