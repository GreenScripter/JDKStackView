package stackview;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Label;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.util.Printer;

import stackview.Stack.StackOp;

public class StackAlter {

	public AbstractInsnNode node;

	List<Label> jumpTargets = new ArrayList<>();
	List<StackOp> remove = new ArrayList<>();
	List<StackOp> add = new ArrayList<>();

	public StackAlter(AbstractInsnNode node) {
		this.node = node;
	}

	public static StackAlter alter(AbstractInsnNode node) {
		return new StackAlter(node);
	}

	public StackAlter add(StackOp... entries) {
		for (var v : entries) {
			add.add(v);
		}
		return this;
	}

	public StackAlter remove(StackOp... entries) {
		for (var v : entries) {
			remove.add(v);
		}
		return this;
	}

	public String toString() {
		return "StackAlter [" + (node != null ? "node=" + Printer.OPCODES[node.getOpcode()] + ", " : "") + (remove != null ? "remove=" + remove + ", " : "") + (add != null ? "add=" + add : "") + "]";
	}

}