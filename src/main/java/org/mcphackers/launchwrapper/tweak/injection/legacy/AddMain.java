package org.mcphackers.launchwrapper.tweak.injection.legacy;

import static org.mcphackers.launchwrapper.util.InsnHelper.*;
import static org.mcphackers.rdi.util.InsnHelper.*;
import static org.objectweb.asm.Opcodes.*;

import org.mcphackers.launchwrapper.LaunchConfig;
import org.mcphackers.launchwrapper.tweak.injection.InjectionWithContext;
import org.mcphackers.launchwrapper.util.ClassNodeSource;
import org.mcphackers.rdi.injector.data.Access;
import org.mcphackers.rdi.util.IdentifyCall;
import org.mcphackers.rdi.util.NodeHelper;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class AddMain extends InjectionWithContext<LegacyTweakContext> {
    
	public AddMain(LegacyTweakContext storage) {
        super(storage);
    }

    @Override
    public String name() {
        return "Add main";
    }

    @Override
	public boolean required() {
		return true;
	}

    @Override
    public boolean apply(ClassNodeSource source, LaunchConfig config) {
        patchMinecraftInit(config);

		if(context.main == null) {
			context.minecraft.methods.add(context.main = getMain(source, config));
		} else {
			context.minecraft.methods.remove(context.main);
			context.minecraft.methods.add(context.main = getMain(source, config));
		}
        return true;
    }

	private void patchMinecraftInit(LaunchConfig config) {
		for(MethodNode m : context.minecraft.methods) {
			if(m.name.equals("<init>")) {
				InsnList insert = new InsnList();
				AbstractInsnNode insn = m.instructions.getFirst();
				boolean widthReplaced = false;
				boolean heightReplaced = false;
				boolean fullscreenReplaced = false;
				while(insn != null) {
					AbstractInsnNode[] insns = fill(insn, 3);
					if(context.fullscreenField != null
					&& compareInsn(insns[0], ALOAD)
					&& compareInsn(insns[2], PUTFIELD, context.minecraft.name, context.fullscreenField.name, context.fullscreenField.desc)) {
						m.instructions.set(insns[1], booleanInsn(config.fullscreen.get()));
					}
					if(context.width != null && context.height != null) {
						if(compareInsn(insns[0], ALOAD)
						&& compareInsn(insns[2], PUTFIELD, context.minecraft.name, context.width.name, context.width.desc)) {
							m.instructions.set(insns[1], intInsn(config.width.get()));
							widthReplaced = true;
						} else
						if(compareInsn(insns[0], ALOAD)
						&& compareInsn(insns[2], PUTFIELD, context.minecraft.name, context.height.name, context.height.desc)) {
							m.instructions.set(insns[1], intInsn(config.height.get()));
							heightReplaced = true;
						}
					}
					insn = nextInsn(insn);
				}
				int thisIndex = 0;
				if(context.width != null && context.height != null) {
					if(!widthReplaced) {
						insert.add(new VarInsnNode(ALOAD, thisIndex));
						insert.add(intInsn(config.width.get()));
						insert.add(new FieldInsnNode(PUTFIELD, context.minecraft.name, context.width.name, context.width.desc));
					}
					if(!heightReplaced) {
						insert.add(new VarInsnNode(ALOAD, thisIndex));
						insert.add(intInsn(config.height.get()));
						insert.add(new FieldInsnNode(PUTFIELD, context.minecraft.name, context.height.name, context.height.desc));
					}
				}
				if(!fullscreenReplaced && context.fullscreenField != null) {
					insert.add(new VarInsnNode(ALOAD, thisIndex));
					insert.add(booleanInsn(config.fullscreen.get()));
					insert.add(new FieldInsnNode(PUTFIELD, context.minecraft.name, context.fullscreenField.name, context.fullscreenField.desc));
				}
				if(context.defaultWidth != null && context.defaultHeight != null) {
					insert.add(new VarInsnNode(ALOAD, thisIndex));
					insert.add(intInsn(config.width.get()));
					insert.add(new FieldInsnNode(PUTFIELD, context.minecraft.name, context.defaultWidth.name, context.defaultWidth.desc));
					insert.add(new VarInsnNode(ALOAD, thisIndex));
					insert.add(intInsn(config.height.get()));
					insert.add(new FieldInsnNode(PUTFIELD, context.minecraft.name, context.defaultHeight.name, context.defaultHeight.desc));
				}
				m.instructions.insert(getSuper(m.instructions.getFirst()), insert);
			}
		}
	}


    protected MethodNode getMain(ClassNodeSource source, LaunchConfig config) {
		MethodNode node = new MethodNode(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
		InsnList insns = node.instructions;

		final int appletIndex = 1;
		final int frameIndex = 2;
		final int mcIndex = 3;
		final int threadIndex = 4;

		final String listenerClass = "org/mcphackers/launchwrapper/inject/WindowListener";

		String mcField = null;
		String mcDesc = "L" + context.minecraft.name + ";";
		boolean invokeAppletInit = patchAppletInit(source, config);
		if(invokeAppletInit) {
			for(FieldNode field : context.minecraftApplet.fields) {
				if(mcDesc.equals(field.desc)) {
					mcField = field.name;
				}
			}
			insns.add(new TypeInsnNode(NEW, context.minecraftApplet.name));
			insns.add(new InsnNode(DUP));
			insns.add(new MethodInsnNode(INVOKESPECIAL, context.minecraftApplet.name, "<init>", "()V"));
			insns.add(new VarInsnNode(ASTORE, appletIndex));
			insns.add(new VarInsnNode(ALOAD, appletIndex));
			insns.add(new MethodInsnNode(INVOKESTATIC, "org/mcphackers/launchwrapper/inject/Inject", "getApplet", "()Lorg/mcphackers/launchwrapper/tweak/AppletWrapper;"));
			insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/applet/Applet", "setStub", "(Ljava/applet/AppletStub;)V"));
			insns.add(new VarInsnNode(ALOAD, appletIndex));
			insns.add(new MethodInsnNode(INVOKEVIRTUAL, context.minecraftApplet.name, "init", "()V"));
		}
		if(!config.lwjglFrame.get()) {
			insns.add(new TypeInsnNode(NEW, "java/awt/Frame"));
			insns.add(new InsnNode(DUP));
			insns.add(new LdcInsnNode(config.title.get() == null ? "Minecraft" : config.title.get()));
			insns.add(new MethodInsnNode(INVOKESPECIAL, "java/awt/Frame", "<init>", "(Ljava/lang/String;)V"));
			insns.add(new VarInsnNode(ASTORE, frameIndex));
			insns.add(new VarInsnNode(ALOAD, frameIndex));
			insns.add(booleanInsn(context.isClassic()));
			insns.add(new MethodInsnNode(INVOKESTATIC, "org/mcphackers/launchwrapper/inject/Inject", "getIcon", "(Z)Ljava/awt/image/BufferedImage;"));
			insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/awt/Frame", "setIconImage", "(Ljava/awt/Image;)V"));
			insns.add(new VarInsnNode(ALOAD, frameIndex));
			insns.add(new TypeInsnNode(NEW, "java/awt/BorderLayout"));
			insns.add(new InsnNode(DUP));
			insns.add(new MethodInsnNode(INVOKESPECIAL, "java/awt/BorderLayout", "<init>", "()V"));
			insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/awt/Frame", "setLayout", "(Ljava/awt/LayoutManager;)V"));
			insns.add(new VarInsnNode(ALOAD, frameIndex));
			insns.add(new VarInsnNode(ALOAD, appletIndex));
			insns.add(new LdcInsnNode("Center"));
			insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/awt/Frame", "add", "(Ljava/awt/Component;Ljava/lang/Object;)V"));
			insns.add(new VarInsnNode(ALOAD, frameIndex));
			insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/awt/Frame", "pack", "()V"));
			insns.add(new VarInsnNode(ALOAD, frameIndex));
			insns.add(new InsnNode(ACONST_NULL));
			insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/awt/Frame", "setLocationRelativeTo", "(Ljava/awt/Component;)V"));
		}
		if(invokeAppletInit) {
			insns.add(new VarInsnNode(ALOAD, appletIndex));
			insns.add(new FieldInsnNode(GETFIELD, context.minecraftApplet.name, mcField, mcDesc));
		} else {
			InsnList constructor = getNewMinecraftImpl(context.minecraft, null, config);
			if(constructor != null) {
				insns.add(constructor);
			} else {
				throw new IllegalStateException("Unexpected constructor!");
			}
		}
		insns.add(new VarInsnNode(ASTORE, mcIndex));
		if(context.width != null && context.height != null) {
			insns.add(new VarInsnNode(ALOAD, mcIndex));
			insns.add(intInsn(config.width.get()));
			insns.add(new FieldInsnNode(PUTFIELD, context.minecraft.name, context.width.name, context.width.desc));
			insns.add(new VarInsnNode(ALOAD, mcIndex));
			insns.add(intInsn(config.height.get()));
			insns.add(new FieldInsnNode(PUTFIELD, context.minecraft.name, context.height.name, context.height.desc));
		}
		if(context.fullscreenField != null) {
			insns.add(new VarInsnNode(ALOAD, mcIndex));
			insns.add(booleanInsn(config.fullscreen.get()));
			insns.add(new FieldInsnNode(PUTFIELD, context.minecraft.name, context.fullscreenField.name, context.fullscreenField.desc));
		}
		if(context.defaultWidth != null && context.defaultHeight != null) {
			insns.add(new VarInsnNode(ALOAD, mcIndex));
			insns.add(intInsn(config.width.get()));
			insns.add(new FieldInsnNode(PUTFIELD, context.minecraft.name, context.defaultWidth.name, context.defaultWidth.desc));
			insns.add(new VarInsnNode(ALOAD, mcIndex));
			insns.add(intInsn(config.height.get()));
			insns.add(new FieldInsnNode(PUTFIELD, context.minecraft.name, context.defaultHeight.name, context.defaultHeight.desc));
		}
		if(context.appletMode != null) {
			insns.add(new VarInsnNode(ALOAD, mcIndex));
			insns.add(booleanInsn(config.applet.get()));
			insns.add(new FieldInsnNode(PUTFIELD, context.minecraft.name, context.appletMode.name, context.appletMode.desc));
		}
		if(config.lwjglFrame.get()) {
			insns.add(new TypeInsnNode(NEW, "java/lang/Thread"));
			insns.add(new InsnNode(DUP));
			insns.add(new VarInsnNode(ALOAD, mcIndex));
			insns.add(new LdcInsnNode("Minecraft main thread"));
			insns.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Thread", "<init>", "(Ljava/lang/Runnable;Ljava/lang/String;)V"));
			insns.add(new VarInsnNode(ASTORE, threadIndex));
		}
		if(!config.lwjglFrame.get()) {
			insns.add(new VarInsnNode(ALOAD, frameIndex));
			insns.add(new InsnNode(ICONST_1));
			insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/awt/Frame", "setVisible", "(Z)V"));
			insns.add(new VarInsnNode(ALOAD, frameIndex));
			insns.add(new TypeInsnNode(NEW, listenerClass));
			insns.add(new InsnNode(DUP));
			insns.add(new VarInsnNode(ALOAD, mcIndex));
			insns.add(new MethodInsnNode(INVOKESPECIAL, listenerClass, "<init>", "(L" + context.minecraft.name + ";)V"));
			insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/awt/Frame", "addWindowListener", "(Ljava/awt/event/WindowListener;)V"));
			createWindowListener(source, listenerClass);
		} else {
			insns.add(new VarInsnNode(ALOAD, threadIndex));
			insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Thread", "start", "()V"));
		}
		insns.add(new InsnNode(RETURN));
		return node;
	}

	protected boolean patchAppletInit(ClassNodeSource source, LaunchConfig config) {
		if(context.minecraftApplet == null) {
			return false;
		}
		String mcField = null;
		String canvasField = null;
		String mcDesc = "L" + context.minecraft.name + ";";
		MethodNode init = NodeHelper.getMethod(context.minecraftApplet, "init", "()V");
		if(init == null) {
			return false;
		}
		for(FieldNode field : context.minecraftApplet.fields) {
			if(mcDesc.equals(field.desc)) {
				field.access = Access.Level.PUBLIC.setAccess(field.access);
				mcField = field.name;
			}
			if("Ljava/awt/Canvas;".equals(field.desc)) {
				canvasField = field.name;
			}
		}
		AbstractInsnNode insn = init.instructions.getFirst();
		while(insn != null) {
			AbstractInsnNode[] insns2 = fill(insn, 6);
			if(compareInsn(insns2[1], PUTFIELD, context.minecraftApplet.name, mcField, mcDesc)
			&& compareInsn(insns2[0], INVOKESPECIAL, null, "<init>")) {
				MethodInsnNode invoke = (MethodInsnNode) insns2[0];
				InsnList constructor = getNewMinecraftImpl(source.getClass(invoke.owner), canvasField, config);
				if(constructor != null) {
					InsnList insns = new InsnList();
					insns.add(new VarInsnNode(ALOAD, 0));
					insns.add(new FieldInsnNode(GETFIELD, context.minecraftApplet.name, canvasField, "Ljava/awt/Canvas;"));
					insns.add(new TypeInsnNode(NEW, "java/awt/Dimension"));
					insns.add(new InsnNode(DUP));
					insns.add(intInsn(config.width.get()));
					insns.add(intInsn(config.height.get()));
					insns.add(new MethodInsnNode(INVOKESPECIAL, "java/awt/Dimension", "<init>", "(II)V"));
					insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/awt/Canvas", "setPreferredSize", "(Ljava/awt/Dimension;)V"));
					init.instructions.insert(insns2[1], insns);
					init.instructions.insertBefore(insns2[1], constructor);
					IdentifyCall call = new IdentifyCall(invoke);
					AbstractInsnNode newInsn = call.getArgument(0)[0].getPrevious();
					if(newInsn.getOpcode() == NEW) {
						init.instructions.remove(newInsn);
					}
					for(AbstractInsnNode[] arg : call.getArguments()) {
						remove(init.instructions, arg);
					}
					init.instructions.remove(invoke);
				}
				insn = insns2[1];
			}
			if(compareInsn(insns2[0], ALOAD, 0)
			&& compareInsn(insns2[1], GETFIELD, context.minecraftApplet.name, mcField, mcDesc)
			&& compareInsn(insns2[2], NEW)
			&& compareInsn(insns2[3], DUP)
			&& compareInsn(insns2[4], INVOKESPECIAL, null, "<init>", "()V")
			&& compareInsn(insns2[5], PUTFIELD, context.minecraft.name)) {
				TypeInsnNode type = (TypeInsnNode) insns2[2];
				ClassNode node = source.getClass(type.desc);
				MethodNode method = NodeHelper.getMethod(node, "<init>", "()V");
				AbstractInsnNode[] insns3 = fill(nextInsn(method.instructions.getFirst()), 4);
				if(compareInsn(insns3[0], ALOAD, 0)
				&& compareInsn(insns3[1], LDC, "DemoUser")
				&& compareInsn(insns3[2], LDC, "n/a")
				&& compareInsn(insns3[3], INVOKESPECIAL, node.superName, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V")) {
					InsnList insert = new InsnList();
					insert.add(new LdcInsnNode(config.username.get()));
					insert.add(new LdcInsnNode(config.session.get()));
					method.instructions.insert(insns2[3], insert);
					method.instructions.set(insns2[2], new TypeInsnNode(NEW, node.superName));
					method.instructions.set(insns2[4], new MethodInsnNode(INVOKESPECIAL, node.superName, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V"));
				}
			}
			if(compareInsn(insns2[0], ALOAD, 0)
			&& compareInsn(insns2[1], GETFIELD, context.minecraftApplet.name, mcField, mcDesc)
			&& compareInsn(insns2[2], LDC, "79.136.77.240")
			&& compareInsn(insns2[3], SIPUSH)
			&& compareInsn(insns2[4], INVOKEVIRTUAL, context.minecraft.name, null, "(Ljava/lang/String;I)V")) {
				LabelNode label = new LabelNode();
				init.instructions.remove(insns2[2]);
				init.instructions.remove(insns2[3]);
				InsnList insert = new InsnList();
				insert.add(new VarInsnNode(ALOAD, 0));
				insert.add(new LdcInsnNode("server"));
				insert.add(new MethodInsnNode(INVOKEVIRTUAL, context.minecraftApplet.name, "getParameter", "(Ljava/lang/String;)Ljava/lang/String;"));
				insert.add(new VarInsnNode(ALOAD, 0));
				insert.add(new LdcInsnNode("port"));
				insert.add(new MethodInsnNode(INVOKEVIRTUAL, context.minecraftApplet.name, "getParameter", "(Ljava/lang/String;)Ljava/lang/String;"));
				insert.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I"));
				init.instructions.insertBefore(insns2[4], insert);
				insert = new InsnList();
				insert.add(new VarInsnNode(ALOAD, 0));
				insert.add(new LdcInsnNode("server"));
				insert.add(new MethodInsnNode(INVOKEVIRTUAL, context.minecraftApplet.name, "getParameter", "(Ljava/lang/String;)Ljava/lang/String;"));
				insert.add(new JumpInsnNode(IFNULL, label));
				insert.add(new VarInsnNode(ALOAD, 0));
				insert.add(new LdcInsnNode("port"));
				insert.add(new MethodInsnNode(INVOKEVIRTUAL, context.minecraftApplet.name, "getParameter", "(Ljava/lang/String;)Ljava/lang/String;"));
				insert.add(new JumpInsnNode(IFNULL, label));
				init.instructions.insertBefore(insns2[0], insert);
				init.instructions.insert(insns2[4], label);
			}
			insn = nextInsn(insn);
		}
		source.overrideClass(context.minecraftApplet);
		return true;
	}

	private void createWindowListener(ClassNodeSource source, String listenerClass) {
		context.running.access = Access.Level.PUBLIC.setAccess(context.running.access);

		ClassNode node = new ClassNode();
		node.visit(49, ACC_PUBLIC, listenerClass, null, "java/awt/event/WindowAdapter", null);
		node.fields.add(new FieldNode(ACC_PRIVATE, "mc", "L" + context.minecraft.name + ";", null, null));
		MethodNode init = new MethodNode(ACC_PUBLIC, "<init>", "(L" + context.minecraft.name + ";)V", null, null);
		InsnList insns = init.instructions;
		insns.add(new VarInsnNode(ALOAD, 0));
		insns.add(new VarInsnNode(ALOAD, 1));
		insns.add(new FieldInsnNode(PUTFIELD, listenerClass, "mc", "L" + context.minecraft.name + ";"));
		insns.add(new VarInsnNode(ALOAD, 0));
		insns.add(new MethodInsnNode(INVOKESPECIAL, "java/awt/event/WindowAdapter", "<init>", "()V"));
		insns.add(new InsnNode(RETURN));
		node.methods.add(init);

		MethodNode windowClosing = new MethodNode(ACC_PUBLIC, "windowClosing", "(Ljava/awt/event/WindowEvent;)V", null, null);
		insns = windowClosing.instructions;

		insns.add(new VarInsnNode(ALOAD, 0));
		insns.add(new FieldInsnNode(GETFIELD, listenerClass, "mc", "L" + context.minecraft.name + ";"));
		insns.add(new InsnNode(ICONST_0));
		insns.add(new FieldInsnNode(PUTFIELD, context.minecraft.name, context.running.name, context.running.desc));
		insns.add(new InsnNode(RETURN));
		node.methods.add(windowClosing);
		source.overrideClass(node);
	}

	private InsnList getNewMinecraftImpl(ClassNode minecraftImpl, String canvasField, LaunchConfig config) {
		MethodNode init = null;
		for(MethodNode m : minecraftImpl.methods) {
			if(m.name.equals("<init>")) {
				init = m;
			}
		}
		if(init == null) {
			throw new NullPointerException();
		}

		InsnList insns = new InsnList();
		insns.add(new TypeInsnNode(NEW, minecraftImpl.name));
		insns.add(new InsnNode(DUP));

		Type[] types = Type.getArgumentTypes(init.desc);

		int i = 0;
		for(Type type : types) {
			String desc = type.getDescriptor();
			if(desc.equals("Ljava/awt/Canvas;")) {
				if(config.lwjglFrame.get()) {
					insns.add(new InsnNode(ACONST_NULL));
				} else {
					insns.add(new VarInsnNode(ALOAD, 0));
					insns.add(new FieldInsnNode(GETFIELD, context.minecraftApplet.name, canvasField, "Ljava/awt/Canvas;"));
				}
			} else if(desc.equals("Ljava/awt/Component;")) {
				if(config.lwjglFrame.get()) {
					insns.add(new InsnNode(ACONST_NULL));
				} else {
					insns.add(new VarInsnNode(ALOAD, 0));
				}
			} else if(context.minecraftApplet != null && desc.equals("L" + context.minecraftApplet.name + ";")) {
				insns.add(new VarInsnNode(ALOAD, 0));
			} else if(desc.equals("I")) {
				if(i == 0) {
					insns.add(intInsn(config.width.get()));
				} else if(i == 1) {
					insns.add(intInsn(config.height.get()));
				} else {
					return null;
					// throw new IllegalStateException("Unexpected constructor: " + init.desc);
				}
				i++;
			} else if(desc.equals("Z")) {
				insns.add(booleanInsn(config.fullscreen.get()));
			} else {
				return null;
				// throw new IllegalStateException("Unexpected constructor: " + init.desc);
			}
		}
		insns.add(new MethodInsnNode(INVOKESPECIAL, minecraftImpl.name, "<init>", init.desc));
		return insns;
	}
}
