package stackview;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Type;

public class Stack {

	public List<EntryValue> entries = new ArrayList<>();
	public int max;

	public Stack(int stackSize) {
		max = stackSize;
	}

	public Stack(Stack other) {
		this.entries.addAll(other.entries);
		this.max = other.max;
	}

	public int getSize() {
		return entries.size();
	}

	public String toString() {
		return "Stack [" + (entries != null ? "entries=" + entries + ", " : "") + "max=" + max + "]";
	}

	public EntryValue popRefOrRet() {
		if (entries.isEmpty()) {
			throw new IllegalStateException("Cannot pop from empty stack.");
		}

		EntryValue there = entries.get(entries.size() - 1);

		if (there.getType() != EntryType.REFERENCE && there.getType() != EntryType.RETURN_ADDRESS) {
			throw new IllegalStateException("Cannot pop " + there + " as a reference or return address.");
		}
		entries.remove(entries.size() - 1);

		return there;
	}

	public EntryValue popAny1() {
		if (entries.isEmpty()) {
			throw new IllegalStateException("Cannot pop from empty stack.");
		}

		EntryValue there = entries.get(entries.size() - 1);

		if (there.getType() == EntryType.LONG_2ND || there.getType() == EntryType.DOUBLE_2ND) {
			throw new IllegalStateException("Cannot pop single " + there + " from stack.");
		}
		entries.remove(entries.size() - 1);

		return there;
	}

	public EntryValue[] popAny2() {
		if (entries.size() < 2) {
			throw new IllegalStateException("Cannot pop 2 from stack with less than 2 elements.");
		}

		EntryValue there = entries.get(entries.size() - 1);
		EntryValue there2 = entries.get(entries.size() - 2);

		if (there2.getType() == EntryType.LONG_2ND || there2.getType() == EntryType.DOUBLE_2ND) {
			throw new IllegalStateException("Cannot pop single " + there2 + " from stack.");
		}

		entries.remove(entries.size() - 1);
		entries.remove(entries.size() - 1);

		return new EntryValue[] { there2, there };
	}

	public void push2(EntryValue[] values) {
		if (values.length != 2) throw new IllegalStateException("Can't push 2 " + values.length + " != 2 values.");
		if (getSize() + 2 > max) {
			throw new IllegalStateException("Cannot push 2 to full stack.");
		}
		push(values[0]);
		if (values[0].getType() == EntryType.LONG) {
			if (values[1].getType() != EntryType.LONG_2ND) {
				throw new IllegalStateException("Invalid long push, " + values[1] + " != " + EntryType.LONG_2ND);
			}
			return;
		}
		if (values[0].getType() == EntryType.DOUBLE) {
			if (values[1].getType() != EntryType.DOUBLE_2ND) {
				throw new IllegalStateException("Invalid double push, " + values[1] + " != " + EntryType.DOUBLE_2ND);
			}
			return;
		}
		push(values[1]);

	}

	public EntryValue pop(EntryType type) {
		if (type == EntryType.LONG_2ND || type == EntryType.DOUBLE_2ND) throw new IllegalStateException("Cannot pop " + type + " from stack.");
		return popImpl(type);
	}

	private EntryValue popImpl(EntryType type) {
		if (entries.isEmpty()) {
			throw new IllegalStateException("Cannot pop " + type + " from empty stack.");
		}

		if (type == EntryType.LONG) {
			popImpl(EntryType.LONG_2ND);
		}

		if (type == EntryType.DOUBLE) {
			popImpl(EntryType.DOUBLE_2ND);
		}

		EntryValue there = entries.get(entries.size() - 1);
		if (there.getType() == type) {
			entries.remove(entries.size() - 1);
		} else {
			throw new IllegalStateException("Cannot pop " + type + " from stack, " + there + " is on top.");
		}
		return there;
	}

	public void push(EntryType type, int source, List<EntryValue> parents) {
		push(new EntryValue(type, source, parents));
	}

	public void push(EntryValue value) {
		if (value.getType() == EntryType.LONG_2ND || value.getType() == EntryType.DOUBLE_2ND) throw new IllegalStateException("Cannot push " + value.getType() + " from stack.");
		pushImpl(value);
	}

	public EntryValue getTop(int down) {
		for (int i = entries.size() - 1; i >= entries.size() - 1 - down; i--) {
			if (entries.get(i).getType() == EntryType.DOUBLE_2ND) {
				down++;
				continue;
			}
			if (entries.get(i).getType() == EntryType.LONG_2ND) {
				down++;
				continue;
			}
			if (i == entries.size() - 1 - down) {
				return entries.get(i);
			}
		}
		return null;
	}

	public void setTopKnown(int down, String s) {
		for (int i = entries.size() - 1; i >= entries.size() - 1 - down; i--) {
			if (entries.get(i).getType() == EntryType.DOUBLE_2ND) {
				down++;
				continue;
			}
			if (entries.get(i).getType() == EntryType.LONG_2ND) {
				down++;
				continue;
			}
			if (i == entries.size() - 1 - down) {
				entries.set(i, entries.get(i).known(s));
				return;
			}
		}
	}

	private void pushImpl(EntryValue value) {
		if (getSize() + 1 > max) {
			throw new IllegalStateException("Cannot push " + value.getType() + " to full stack.");
		}
		entries.add(value);

		if (value.getType() == EntryType.LONG) {
			pushImpl(new EntryValue(EntryType.LONG_2ND, value.getSourceHistory()));
		}

		if (value.getType() == EntryType.DOUBLE) {
			pushImpl(new EntryValue(EntryType.DOUBLE_2ND, value.getSourceHistory()));
		}
	}

	public static StackOp fromMethodReturn(String t) {
		return fromMethodReturn(Type.getType(t));
	}

	public static StackOp fromMethodReturn(Type t) {
		if (t.getSort() != Type.METHOD) throw new IllegalArgumentException("Invalid type " + t);

		return from(t.getReturnType());
	}

	public static StackOp[] fromMethodArguments(String t) {
		return fromMethodArguments(Type.getType(t));
	}

	public static StackOp[] fromMethodArgumentsPop(String t) {
		StackOp[] pops = fromMethodArguments(Type.getType(t));
		for (int i = 0; i < pops.length / 2; i++) {
			StackOp tmp = pops[i];
			pops[i] = pops[pops.length - 1 - i];
			pops[pops.length - 1 - i] = tmp;
		}
		return pops;
	}

	public static StackOp[] fromMethodArguments(Type t) {
		if (t.getSort() != Type.METHOD) throw new IllegalArgumentException("Invalid type " + t);

		StackOp[] entries = new StackOp[t.getArgumentCount()];
		int i = 0;
		for (Type arg : t.getArgumentTypes()) {
			entries[i] = from(arg);
			i++;
		}
		return entries;
	}

	public static StackOp from(String t) {
		return from(Type.getType(t));
	}

	public static StackOp from(Type t) {
		switch (t.getSort()) {
			case Type.BOOLEAN:
			case Type.BYTE:
			case Type.CHAR:
			case Type.INT:
			case Type.SHORT:
				return StackOp.INT;
			case Type.ARRAY:
			case Type.OBJECT:
				return StackOp.REFERENCE;
			case Type.FLOAT:
				return StackOp.FLOAT;
			case Type.DOUBLE:
				return StackOp.DOUBLE;
			case Type.LONG:
				return StackOp.LONG;
			default:
				throw new IllegalArgumentException("Invalid type " + t);
		}
	}

	public static enum StackOp {

		REFERENCE(1), INT(1), LONG(2), FLOAT(1), DOUBLE(2), RETURN_ADDRESS(1), //
		ANY_1(1), ANY_2(2), FRAME(0), CLEAR(0), JUMP(0), REFERENCE_OR_RETURN_ADDRESS(1), THROW(0), RET(0);

		public final int offset;

		StackOp(int o) {
			offset = o;
		}

		public EntryType entry() {
			if (this.ordinal() > RETURN_ADDRESS.ordinal()) {
				return null;
			}
			return EntryType.values()[ordinal()];
		}
	}
}
