package stackview;

import java.util.Arrays;

public class LocalVariables {

	public EntryValue[] entries;

	public int count;

	public LocalVariables(int max) {
		count = max;
		entries = new EntryValue[max];
		for (int i = 0; i < entries.length; i++) {
			entries[i] = new EntryValue(null);
		}
	}

	public LocalVariables(LocalVariables other) {
		count = other.count;
		entries = new EntryValue[count];
		for (int i = 0; i < entries.length; i++) {
			entries[i] = other.entries[i];
		}
	}

	public void set(int index, EntryValue type, int source) {

		if (type.getType() == EntryType.LONG_2ND || type.getType() == EntryType.DOUBLE_2ND) {
			throw new IllegalStateException("Cannot set " + index + " to " + type);
		}

		setImpl(index, type, source);
	}

	private void setImpl(int index, EntryValue type, int source) {
		if (index < 0) throw new IllegalStateException("Can't set local variable index " + index);
		if (index >= count) throw new IllegalStateException("Local variable index " + index + " for " + type + " outside of range " + count);
		if (type.getType() == EntryType.DOUBLE) {
			setImpl(index + 1, new EntryValue(EntryType.DOUBLE_2ND, type.getSourceHistory()), source);
		}
		if (type.getType() == EntryType.LONG) {
			setImpl(index + 1, new EntryValue(EntryType.LONG_2ND, type.getSourceHistory()), source);
		}

		EntryValue old = entries[index];
		if (old.getType() == EntryType.DOUBLE) {
			entries[index + 1] = new EntryValue(null, source);
		}
		if (old.getType() == EntryType.DOUBLE_2ND) {
			entries[index - 1] = new EntryValue(null, source);
		}
		if (old.getType() == EntryType.LONG) {
			entries[index + 1] = new EntryValue(null, source);
		}
		if (old.getType() == EntryType.LONG_2ND) {
			entries[index - 1] = new EntryValue(null, source);
		}
		entries[index] = type.move(source);

	}

	public EntryValue get(int index, EntryType type) {

		if (type == EntryType.LONG_2ND || type == EntryType.DOUBLE_2ND) {
			throw new IllegalStateException("Cannot set " + index + " to " + type);
		}

		return getImpl(index, type);
	}

	private EntryValue getImpl(int index, EntryType type) {
		if (index < 0) throw new IllegalStateException("Can't set local variable index " + index);
		if (index >= count) throw new IllegalStateException("Local variable index " + index + " for " + type + " outside of range " + count);

		if (type != entries[index].getType()) {
			throw new IllegalStateException("Unable to read " + type + " from local variable " + index + ", found " + entries[index]);
		}

		if (type == EntryType.DOUBLE) {
			getImpl(index + 1, EntryType.DOUBLE_2ND);
		}
		if (type == EntryType.LONG) {
			getImpl(index + 1, EntryType.LONG_2ND);
		}
		return entries[index];
	}

	public String toString() {
		return "LocalVariables [" + (entries != null ? "entries=" + Arrays.toString(entries) + ", " : "") + "count=" + count + "]";
	}

}
