import static org.objectweb.asm.Opcodes.*;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.TraceClassVisitor;

import stackview.InstructionSpec;
import stackview.Simulator;

public class ASMTest {

	public static void main(String[] args) throws Exception {
		ClassPrinter cp = new ClassPrinter();
		ClassReader cr = new ClassReader("Example");
		cr.accept(cp, 0);
		TraceClassVisitor trace = new TraceClassVisitor(/*new ClassWriter(0), new ASMifier(), */new PrintWriter(System.out));
		cr.accept(trace, 0);

		ClassWriter cw = new ClassWriter(0);
		cw.visit(V21, ACC_PUBLIC + ACC_ABSTRACT + ACC_INTERFACE, "pkg/Comparable", null, "java/lang/Object", new String[] {});
		cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, "LESS", "I", null, Integer.valueOf(-1)).visitEnd();
		cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, "EQUAL", "I", null, Integer.valueOf(0)).visitEnd();
		cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, "GREATER", "I", null, Integer.valueOf(1)).visitEnd();
		MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "run", "()J", null, null);
		mv.visitCode();
		mv.visitInsn(LCONST_1);
		mv.visitVarInsn(LSTORE, 0);
		mv.visitInsn(LCONST_0);
		mv.visitVarInsn(LSTORE, 2);
		mv.visitVarInsn(LLOAD, 0);
		mv.visitInsn(LRETURN);
		mv.visitMaxs(2, 4);
		mv.visitEnd();
		cw.visitEnd();
		byte[] b = cw.toByteArray();
		Files.write(new File("test.class").toPath(), b);

		PublicClassLoader loader = new PublicClassLoader();
		Class<?> clas = loader.defineClass(b);
		System.out.println(clas);
		System.out.println(clas.getMethod("run").invoke(null));

		ClassNode cn = new ClassNode();
		cr.accept(cn, ClassReader.EXPAND_FRAMES);
		for (MethodNode mn : cn.methods) {
			System.out.println(((mn.access & ACC_STATIC) != 0 ? "static " : "") + mn.name + mn.desc);
			//			if (mn.name.equals("test")) {
			InstructionSpec spec = new InstructionSpec(mn.desc, (mn.access & ACC_STATIC) != 0);
			mn.accept(spec);
			Simulator sim = new Simulator(spec);
			sim.initLocals();
			System.out.println(sim);

			while (!sim.state.done) {
				System.out.println(sim.currentInstruction());
				sim.performPops();
				sim.performPushes();
				if (!sim.state.done) sim.performJump(null);
				System.out.println(sim.locals);
				System.out.println(sim.stack);
				System.out.println();
			}

			//			}
		}

	}

	public static class PublicClassLoader extends ClassLoader {

		@SuppressWarnings("deprecation")
		public Class<?> defineClass(byte[] b) {
			return this.defineClass(b, 0, b.length);
		}

	}

	public static void test(boolean v) {

		System.out.println(v);

		return;
	}

	public static class ClassPrinter extends ClassVisitor {

		public ClassPrinter() {
			super(ASM9);
		}

		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			System.out.println();
			System.out.println(name + "\n" + desc);
			return new MethodPrinter();
		}
	}

	public static class MethodPrinter extends MethodVisitor {

		public MethodPrinter() {
			super(ASM9);
		}

		public void visitMaxs(int maxStack, int maxLocals) {
			System.out.println(maxStack + " " + maxLocals);
		}

		public void visitInsn(int opcode) {
			//			System.out.println(Printer.OPCODES[opcode]);
		}
	}
}
