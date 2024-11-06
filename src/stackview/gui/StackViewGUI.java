package stackview.gui;

import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.util.TraceClassVisitor;

import stackview.EntryHistory;
import stackview.InstructionSpec;
import stackview.Simulator;
import stackview.Simulator.State;
import stackview.StackAlter;

public class StackViewGUI {

	public static void main(String[] args) throws Exception {
		new StackViewGUI(new File("."));
	}

	static record ClassMethod(MethodNode method, InstructionSpec spec) {}

	List<ClassMethod> methods = new ArrayList<>();

	JFrame frame = new JFrame("StackViewGUI");
	JComboBox<String> options = new JComboBox<>();
	JPanel center = new JPanel();
	File open;

	public StackViewGUI(File f) throws InvocationTargetException, InterruptedException {

		JFileChooser fc = new JFileChooser(f);
		SwingUtilities.invokeAndWait(() -> {
			while (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
				;
		});
		open = fc.getSelectedFile();

		try {
			ClassReader cr = new ClassReader(new FileInputStream(open));
			ClassNode cn = new ClassNode();
			cr.accept(cn, ClassReader.EXPAND_FRAMES);

			TraceClassVisitor trace = new TraceClassVisitor(/*new ClassWriter(0), new ASMifier(), */new PrintWriter(System.out));
			cn.accept(trace);

			for (MethodNode mn : cn.methods) {
				InstructionSpec spec = new InstructionSpec(mn.desc, (mn.access & ACC_STATIC) != 0);
				mn.accept(spec);

				methods.add(new ClassMethod(mn, spec));
			}

			initWindow();
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, e.getMessage(), e.getClass().toString(), JOptionPane.ERROR_MESSAGE);
		}

	}

	private void initWindow() {
		frame.setSize(1920, 1080);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		center.setLayout(new BorderLayout());
		frame.setLayout(new BorderLayout());
		frame.setTitle("StackViewGUI: " + open.getName());
		options.addItem("Select a Method");

		for (ClassMethod m : methods) {
			options.addItem(((m.method.access & ACC_STATIC) != 0 ? "static " : "") + m.method.name + m.method.desc);
		}

		JPanel northPanel = new JPanel();
		northPanel.setLayout(new BorderLayout());
		northPanel.add(options);
		JButton open = new JButton("Open");
		northPanel.add(open, BorderLayout.EAST);
		open.addActionListener(e -> {
			frame.setVisible(false);
			new Thread(() -> {
				try {
					new StackViewGUI(this.open);
				} catch (InvocationTargetException e1) {
					e1.printStackTrace();
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}).start();
		});

		frame.add(northPanel, BorderLayout.NORTH);
		options.addItemListener(new ItemListener() {

			public void itemStateChanged(ItemEvent e) {
				if (options.getSelectedIndex() == 0) {
					center.removeAll();
					center.add(new JLabel("Select a Method."), BorderLayout.CENTER);
					frame.revalidate();
					return;
				}
				ClassMethod cm = methods.get(options.getSelectedIndex() - 1);
				showSimulate(cm);
			}
		});
		center.add(new JLabel("Select a Method."), BorderLayout.CENTER);
		frame.add(center, BorderLayout.CENTER);
		frame.setVisible(true);
	}

	private void showSimulate(ClassMethod cm) {
		SimulateView view = new SimulateView(cm);
		center.removeAll();
		center.add(view, BorderLayout.CENTER);
		frame.revalidate();
	}

	class SimulateView extends JPanel {

		ClassMethod classMethod;
		Simulator sim;
		JPanel buttons = new JPanel() {

			protected void paintComponent(Graphics g1) {
				g1.setColor(getBackground());
				g1.drawRect(0, 0, getWidth(), getHeight());
			}
		};
		StateRenderer render;
		List<Simulator> undoStack = new ArrayList<>();
		int undoIndex;

		JButton nextButton = new JButton("Next");

		JPanel rightPanel = new JPanel();
		JButton undoButton = new JButton("Undo");
		JButton redoButton = new JButton("Redo");

		JButton fastForwardButton = new JButton("Fast Forward");
		JComboBox<String> fastForwardDropdown = new JComboBox<>();

		JButton throwButton = new JButton("Throw");
		JComboBox<String> throwDropdown = new JComboBox<>();

		JCheckBox history = new JCheckBox("History", true);

		SimulateView(ClassMethod cm) {
			classMethod = cm;
			sim = new Simulator(cm.spec);
			setLayout(new BorderLayout());
			buttons.setLayout(new FlowLayout(FlowLayout.LEADING));
			JPanel bottomPanel = new JPanel();
			bottomPanel.setLayout(new BorderLayout());
			bottomPanel.add(buttons, BorderLayout.WEST);
			this.add(bottomPanel, BorderLayout.SOUTH);
			render = new StateRenderer();
			this.add(render, BorderLayout.CENTER);

			nextButton.addActionListener(e -> {
				stepForward();
				saveUndo();
				updateButtons();
			});

			undoButton.addActionListener(e -> {
				undo();
				updateButtons();
			});

			redoButton.addActionListener(e -> {
				redo();
				updateButtons();
			});

			throwButton.addActionListener(e -> {
				var catches = sim.getActiveCatches();
				if (catches.size() > throwDropdown.getSelectedIndex() && throwDropdown.getSelectedIndex() >= 0) {
					jump(catches.get(throwDropdown.getSelectedIndex()).handler.getLabel());
					saveUndo();
					updateButtons();
				}
			});

			fastForwardButton.addActionListener(e -> {
				long start = System.currentTimeMillis();
				int steps = 0;
				while (!sim.state.done) {
					stepForward();
					steps++;
					if (sim.state == State.JUMP) {
						if (sim.getExpectedJumpTarget() == null) {
							break;
						}
					}
					if (sim.instruction - 1 >= 0 && classMethod.spec.alters.get(sim.instruction - 1).node instanceof LabelNode l) {
						if (classMethod.spec.labelNames.get(l.getLabel()).equals(fastForwardDropdown.getSelectedItem())) {
							break;
						}
					}
					if (System.currentTimeMillis() - start > 100) {
						break;
					}
				}
				System.out.println("Fast forwarded " + steps + " in " + (System.currentTimeMillis() - start) + " ms.");
				saveUndo();
				updateButtons();
			});
			for (var alter : classMethod.spec.alters) {
				if (alter.node instanceof LabelNode l) {
					fastForwardDropdown.addItem(classMethod.spec.labelNames.get(l.getLabel()));
				}
			}

			rightPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));

			rightPanel.add(history);
			rightPanel.add(fastForwardButton);
			rightPanel.add(fastForwardDropdown);
			rightPanel.add(throwButton);
			rightPanel.add(throwDropdown);
			rightPanel.add(undoButton);
			rightPanel.add(redoButton);
			bottomPanel.add(rightPanel, BorderLayout.EAST);

			updateButtons();

		}

		private void stepForward() {
			if (sim.state == State.JUMP) {
				if (sim.getExpectedJumpTarget() != null) {
					jump(sim.getExpectedJumpTarget().orElse(null));
				}
			}
			if (sim.state == State.INIT) {
				saveUndo();
				sim.initLocals();
			}
			if (sim.state == State.POP) {
				sim.performPops();
			}
			if (sim.state == State.PUSH) {
				sim.performPushes();
			}
		}

		private void jump(Label l) {
			sim.performJump(l);
			if (!sim.state.done && sim.getInstruction().node instanceof LabelNode) {
				jump(null);
			} else if (!history.isSelected()) {
				sim.purgeHistory();
			}
		}

		private void saveUndo() {
			while (undoIndex < undoStack.size() - 1) {
				undoStack.removeLast();
			}
			undoStack.add(new Simulator(sim));
			undoIndex = undoStack.size() - 1;
		}

		private void undo() {
			if (canUndo()) {
				undoIndex--;
				sim.become(undoStack.get(undoIndex));
			}
		}

		private void redo() {
			if (canRedo()) {
				undoIndex++;
				sim.become(undoStack.get(undoIndex));
			}
		}

		private boolean canUndo() {
			return undoIndex > 0;
		}

		private boolean canRedo() {
			return undoIndex < undoStack.size() - 1;
		}

		private void updateButtons() {
			render.repaint();
			buttons.removeAll();
			buttons.repaint();
			buttons.add(nextButton);

			switch (sim.state) {
				case INIT: {
					JButton init = new JButton("Init");
					init.addActionListener(e -> {
						saveUndo();
						sim.initLocals();
						saveUndo();
						updateButtons();
					});
					buttons.add(init);
					break;
				}

				case POP: {
					JButton pop = new JButton("Pop");
					pop.addActionListener(e -> {
						sim.performPops();
						saveUndo();
						updateButtons();
					});
					buttons.add(pop);
					break;
				}
				case PUSH: {
					JButton pop = new JButton("Push");
					pop.addActionListener(e -> {
						sim.performPushes();
						saveUndo();
						updateButtons();
					});
					buttons.add(pop);
					break;
				}
				case JUMP: {
					JComboBox<String> jumpTargets = new JComboBox<>();
					List<Label> jumps = new ArrayList<>(sim.getJumps());
					List<TryCatchBlockNode> catches = sim.getActiveCatches();
					if (sim.getInstruction().node.getOpcode() != Opcodes.ATHROW && sim.getInstruction().node.getOpcode() != Opcodes.GOTO) {
						jumps.add(0, null);
					}
					for (Label l : jumps) {
						if (l == null) {
							jumpTargets.addItem("Next instruction");
						} else {
							jumpTargets.addItem(classMethod.spec.labelNames.get(l));
						}
					}
					if (sim.getInstruction().node.getOpcode() != Opcodes.ATHROW) {
						for (TryCatchBlockNode tryCatch : catches) {
							jumpTargets.addItem("Catch " + tryCatch.type + ": " + classMethod.spec.labelNames.get(tryCatch.handler.getLabel()));
							jumps.add(tryCatch.handler.getLabel());

						}
					}
					Optional<Label> expected = sim.getExpectedJumpTarget();
					if (expected != null) {
						Label target = expected.orElse(null);
						for (int i = 0; i < jumps.size(); i++) {
							if (target == jumps.get(i)) {
								jumpTargets.setSelectedIndex(i);
								break;
							}
						}
					}
					JButton pop = new JButton("Jump");
					pop.addActionListener(e -> {
						if (jumpTargets.getSelectedIndex() != -1) {
							jump(jumps.get(jumpTargets.getSelectedIndex()));
						} else {
							sim.state = State.FINISHED;
						}
						saveUndo();
						updateButtons();
					});
					buttons.add(pop);
					buttons.add(jumpTargets);
					break;
				}
				case FINISHED:
					buttons.add(new JLabel("Simulation complete."));
					break;
				case ERRORED:
					buttons.add(new JLabel(sim.errorMessage));
					break;

			}
			nextButton.setEnabled((!sim.state.done && sim.state != State.JUMP) || (sim.state == State.JUMP && sim.getExpectedJumpTarget() != null));
			fastForwardButton.setEnabled((!sim.state.done && sim.state != State.JUMP) || (sim.state == State.JUMP && sim.getExpectedJumpTarget() != null));

			throwDropdown.removeAllItems();
			for (var tryCatch : sim.getActiveCatches()) {
				throwDropdown.addItem(classMethod.spec.labelNames.get(tryCatch.handler.getLabel()) + " " + tryCatch.type);
			}
			throwButton.setEnabled(throwDropdown.getItemCount() > 0);

			undoButton.setEnabled(canUndo());
			redoButton.setEnabled(canRedo());
			buttons.revalidate();
		}

		class StateRenderer extends JPanel implements MouseWheelListener, MouseListener, MouseMotionListener {

			public StateRenderer() {
				this.addMouseWheelListener(this);
				this.addMouseListener(this);
				this.addMouseMotionListener(this);

				int i = 0;
				for (var tryCatch : classMethod.spec.tryCatchBlocks) {
					i++;
					{
						Label l = tryCatch.start.getLabel();
						String already = tryCatchLabels.getOrDefault(l, "");
						already += " try " + i + " " + tryCatch.type;
						tryCatchLabels.put(l, already);
					}
					{
						Label l = tryCatch.end.getLabel();
						String already = tryCatchLabels.getOrDefault(l, "");
						already += " end try " + i + " " + tryCatch.type;
						tryCatchLabels.put(l, already);
					}
					{
						Label l = tryCatch.handler.getLabel();
						String already = tryCatchLabels.getOrDefault(l, "");
						already += " catch " + i + " " + tryCatch.type;
						tryCatchLabels.put(l, already);
					}
				}
			}

			double lineOffset;
			int mouseX;
			int mouseY;
			boolean mouseDownInstructions;
			boolean mouseDownLocals;
			boolean mouseDownStack;

			protected void paintComponent(Graphics g1) {
				Graphics2D g = (Graphics2D) g1;
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g.setColor(Color.white);
				g.fillRect(0, 0, getWidth(), getHeight());
				renderInstructions(g);
				renderLocals(g);
				renderStack(g);
			}

			private void drawArrow(Graphics2D g, double x1, double y1, double x2, double y2, double headLength) {
				Stroke s = g.getStroke();
				g.scale(0.01, 0.01);
				x1 *= 100;
				y1 *= 100;
				x2 *= 100;
				y2 *= 100;
				headLength *= 100;
				headLength = Math.min(headLength, Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)));
				g.setStroke(new BasicStroke((float) headLength / 5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
				double tan = Math.atan2(y2 - y1, x2 - x1);
				double angle = Math.toRadians(180 + 20);
				double rad90 = Math.toRadians(90);
				double width = headLength / 10;
				Polygon poly = new Polygon();
				poly.addPoint((int) Math.round(x2 + Math.cos(tan + angle) * headLength), (int) Math.round(y2 + Math.sin(tan + angle) * headLength));
				poly.addPoint((int) x2, (int) y2);
				poly.addPoint((int) Math.round(x2 + Math.cos(tan - angle) * headLength), (int) Math.round(y2 + Math.sin(tan - angle) * headLength));
				double offsetCosP = Math.cos(tan + rad90) * width;
				double offsetSinP = Math.sin(tan + rad90) * width;
				double offsetCosN = Math.cos(tan - rad90) * width;
				double offsetSinN = Math.sin(tan - rad90) * width;
				poly.addPoint((int) (x2 - Math.cos(tan) * (headLength * 0.94) - offsetCosN), (int) (y2 - Math.sin(tan) * (headLength * 0.94) - offsetSinN));
				poly.addPoint((int) (x1 - offsetCosN), (int) (y1 - offsetSinN));
				poly.addPoint((int) (x1 - offsetCosP), (int) (y1 - offsetSinP));
				poly.addPoint((int) (x2 - Math.cos(tan) * (headLength * 0.94) - offsetCosP), (int) (y2 - Math.sin(tan) * (headLength * 0.94) - offsetSinP));

				g.fillPolygon(poly);
				g.scale(100, 100);
				g.setStroke(s);

			}

			private void renderLocals(Graphics2D g) {
				int width = getWidth() / 6;
				FontMetrics fm = g.getFontMetrics();

				g.translate(4 * width, 0d);

				g.setColor(Color.white);
				g.fillRect(0, 0, width, getHeight());
				g.setColor(Color.black);
				g.drawRect(0, 0, width, getHeight());

				int y = fm.getAscent();

				for (int i = 0; i < sim.locals.count; i++) {
					g.setColor(Color.black);
					g.drawRect(0, y - fm.getAscent(), width, fm.getHeight());
					if (i < sim.locals.entries.length) {
						String name = sim.getLocalName(i);
						String end = sim.locals.entries[i].getKnownValue() == null ? "" : sim.locals.entries[i].getKnownValue();
						if (name != null) {
							g.drawString("Local index " + i + ": " + sim.locals.entries[i].getType() + " (" + name + ") " + end, 1, y);
						} else {
							g.drawString("Local index " + i + ": " + sim.locals.entries[i].getType() + " " + end, 1, y);
						}
					}
					if (i == this.hoverLocalsIndex) {
						var localEntries = sim.locals.entries;
						if (localEntries.length > i) {
							var history = localEntries[i].getSourceHistory();
							g.translate(-4 * width, 0d);
							g.setColor(new Color(0, 0, 0, 128));
							renderSource(g, history, 4 * width, y - fm.getAscent() / 2, 0);
							g.translate(4 * width, 0d);
						}
					}
					y += fm.getHeight();
				}
				g.translate(-4 * width, 0d);

			}

			int fontHeight = 0;

			private void renderStack(Graphics2D g) {
				int width = getWidth() / 6;
				FontMetrics fm = g.getFontMetrics();
				fontHeight = fm.getHeight();

				g.translate(5 * width, 0d);

				g.setColor(Color.white);
				g.fillRect(0, 0, width, getHeight());
				g.setColor(Color.black);
				g.drawRect(0, 0, width, getHeight());

				int y = fm.getAscent();

				for (int i = 0; i < sim.stack.max; i++) {
					g.setColor(Color.black);
					g.drawRect(0, y - fm.getAscent(), width, fm.getHeight());
					if (i < sim.stack.entries.size()) {
						String end = sim.stack.entries.get(i).getKnownValue() == null ? "" : sim.stack.entries.get(i).getKnownValue();

						g.drawString(sim.stack.entries.get(i).getType().toString() + " " + end, 1, y);
					}
					if (i == this.hoverStackIndex) {
						var stackEntries = sim.stack.entries;
						if (stackEntries.size() > i) {
							var history = stackEntries.get(i).getSourceHistory();
							g.translate(-5 * width, 0d);
							g.setColor(new Color(0, 0, 0, 128));
							renderSource(g, history, 5 * width, y - fm.getAscent() / 2, 0);
							g.translate(5 * width, 0d);

						}
					}
					y += fm.getHeight();
				}
				g.translate(-5 * width, 0d);

			}

			private void renderSource(Graphics2D g, EntryHistory ehistory, int px, int py, int red) {
				var history = ehistory.viewHistory();
				FontMetrics fm = g.getFontMetrics();

				boolean first = true;
				for (int j = history.size() - 1; j >= 0; j--) {
					int last = history.get(j);
					if (last == -1) {
						break;
					}
					if (first) {
					} else {
						g.setColor(new Color(red, 0, 255, 128));
					}

					String draw = instructionString(classMethod.spec.alters.get(last), fm, getWidth() / 6 * 4);
					int dx = fm.stringWidth(draw);
					int dy = (int) ((last - lineOffset + 0.5) * fm.getHeight());
					if (first) {
						this.drawArrow(g, px, py, dx, dy, 20);
					} else {
						this.drawArrow(g, Math.max(px, dx), py, Math.max(px, dx), dy, 20);
					}
					px = dx;
					py = dy;
					first = false;
				}
				for (EntryHistory h2 : ehistory.viewSources()) {
					g.setColor(new Color(Math.min(255, red + 80), 255, 0, 128));
					renderSource(g, h2, px, py, Math.min(255, red + 80));
				}

			}

			int scrollBottom = 0;

			Map<Label, String> tryCatchLabels = new HashMap<>();

			private String instructionString(StackAlter alter, FontMetrics fm, int maxWidth) {
				String draw = classMethod.spec.toString(alter.node);
				if (alter.node instanceof LabelNode ln) {
					Label l = ln.getLabel();
					if (classMethod.spec.lineNumbers.containsKey(l)) {
						draw += ": Line " + classMethod.spec.lineNumbers.get(l);
					}
					if (tryCatchLabels.containsKey(l)) {
						draw += tryCatchLabels.get(l);
					}
				} else {
					draw = "  " + draw;
				}

				//Compress type names.
				if (fm.stringWidth(draw) > maxWidth && !draw.contains("\"")) {
					draw = draw.replaceAll("L[a-zA-Z\\/$]*;", "L...;");
				}
				return draw;
			}

			private void renderInstructions(Graphics2D g) {
				int width = getWidth() / 6 * 4;

				FontMetrics fm = g.getFontMetrics();

				g.setColor(Color.white);
				g.fillRect(0, 0, width, getHeight());
				g.setColor(Color.black);
				g.drawRect(0, 0, width, getHeight());

				int y = fm.getAscent();
				var alters = classMethod.spec.alters;
				lineOffset = Math.min(Math.max(0, lineOffset), Math.max(0, alters.size() - getHeight() / fm.getHeight()));
				int index = (int) lineOffset;
				y += (index - lineOffset) * fm.getHeight();
				while (index < alters.size()) {
					var alter = alters.get(index);
					if (index == sim.instruction) {
						if (sim.state == State.FINISHED) {
							g.setColor(Color.green);
						} else if (sim.state == State.ERRORED) {
							g.setColor(Color.red);
						} else {
							g.setColor(Color.blue);
						}
					} else {
						g.setColor(Color.black);
					}

					String draw = instructionString(alter, fm, width);

					g.drawString(draw, 0, y);
					y += fm.getHeight();
					if (y - fm.getHeight() > getHeight()) {
						break;
					}
					index++;
				}
				scrollBottom = index;

				g.setColor(Color.LIGHT_GRAY);
				int scrollStart = (int) (getHeight() / (alters.size() / lineOffset));
				int scrollEnd = (int) (getHeight() / ((double) alters.size() / scrollBottom));
				g.fillRect(width - 6, scrollStart, 4, scrollEnd - scrollStart);
			}

			int hoverStackIndex = -1;

			private void hoverStack() {
				int y = fontHeight;

				for (int i = 0; i < sim.stack.max; i++) {
					if (mouseY < y) {
						hoverStackIndex = i;
						repaint();
						return;
					}
					y += fontHeight;
				}
				hoverStackIndex = -1;
			}

			int hoverLocalsIndex = -1;

			private void hoverLocals() {
				int y = fontHeight;

				for (int i = 0; i < sim.locals.count; i++) {
					if (mouseY < y) {
						hoverLocalsIndex = i;
						repaint();
						return;
					}
					y += fontHeight;
				}
				hoverLocalsIndex = -1;
			}

			boolean draggingScroll = false;
			double dragYoffset = 0;

			public void clickInstructions() {
				int width = getWidth() / 6 * 4;

				if (mouseX >= width - 7) {
					draggingScroll = true;
					int scrollStart = (int) (getHeight() / (classMethod.spec.alters.size() / lineOffset));
					int scrollEnd = (int) (getHeight() / ((double) classMethod.spec.alters.size() / scrollBottom));
					if (mouseY > scrollEnd || mouseY < scrollStart) {
						lineOffset = classMethod.spec.alters.size() * (mouseY / (double) getHeight()) - (scrollBottom - lineOffset) / 2;
					}
					dragYoffset = mouseY - scrollStart;
					repaint();
				}
			}

			public void mouseWheelMoved(MouseWheelEvent e) {
				lineOffset += e.getPreciseWheelRotation();
				repaint();
			}

			public void mouseDragged(MouseEvent e) {
				mouseX = e.getX();
				mouseY = e.getY();

				if (draggingScroll) {
					lineOffset = classMethod.spec.alters.size() * ((mouseY - dragYoffset) / (double) getHeight());

					repaint();
				}
			}

			public void mouseMoved(MouseEvent e) {
				mouseX = e.getX();
				mouseY = e.getY();
				int slice = getWidth() / 6;

				if (hoverStackIndex != -1) {
					hoverStackIndex = -1;
					repaint();
				}

				if (hoverLocalsIndex != -1) {
					hoverLocalsIndex = -1;
					repaint();
				}

				if (mouseX < slice * 4) {

				} else if (mouseX < slice * 5) {
					hoverLocals();
				} else if (mouseX < slice * 6) {
					hoverStack();
				}
			}

			public void mousePressed(MouseEvent e) {
				int slice = getWidth() / 6;
				if (mouseX < slice * 4) {
					mouseDownInstructions = true;
					clickInstructions();
				} else if (mouseX < slice * 5) {
					mouseDownLocals = true;
				} else if (mouseX < slice * 6) {
					mouseDownStack = true;
				}
			}

			public void mouseReleased(MouseEvent e) {
				mouseDownInstructions = false;
				mouseDownLocals = false;
				mouseDownStack = false;

				draggingScroll = false;
			}

			public void mouseClicked(MouseEvent e) {}

			public void mouseEntered(MouseEvent e) {}

			public void mouseExited(MouseEvent e) {}
		}
	}
}
