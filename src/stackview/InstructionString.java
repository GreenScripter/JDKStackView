package stackview;

import java.util.Map;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

public class InstructionString {

	public InstructionString(InstructionSpec spec) {
		this.spec = spec;
	}

	InstructionSpec spec;

	public String toString(AbstractInsnNode inst) {
		TraceMethodVisitor trace = new TraceMethodVisitor(new TextifierLabeled(spec.labelNames));
		inst.accept(trace);
		return trace.p.getText().get(0).toString().trim();

	}

	static class TextifierLabeled extends Textifier {

		public TextifierLabeled(Map<Label, String> names) {
			super(Opcodes.ASM9);
			this.labelNames = names;
		}
	}
}
