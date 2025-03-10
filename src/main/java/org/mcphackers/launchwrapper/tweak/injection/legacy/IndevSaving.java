package org.mcphackers.launchwrapper.tweak.injection.legacy;

import static org.mcphackers.launchwrapper.util.asm.InsnHelper.*;
import static org.objectweb.asm.Opcodes.*;

import java.io.File;
import java.io.IOException;

import org.mcphackers.launchwrapper.Launch;
import org.mcphackers.launchwrapper.LaunchConfig;
import org.mcphackers.launchwrapper.protocol.SaveRequests;
import org.mcphackers.launchwrapper.tweak.injection.InjectionWithContext;
import org.mcphackers.launchwrapper.tweak.injection.MinecraftGetter;
import org.mcphackers.launchwrapper.util.ClassNodeSource;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Makes "online save" buttons work like offline level saving. Saves to ${gameDir}/levels
 */
public class IndevSaving extends InjectionWithContext<MinecraftGetter> {

	public IndevSaving(MinecraftGetter storage) {
		super(storage);
	}

	public String name() {
		return "Indev save patch";
	}

	public boolean required() {
		return false;
	}

	public boolean apply(ClassNodeSource source, LaunchConfig config) {
		ClassNode minecraft = context.getMinecraft();
		ClassNode saveLevelMenu = null;
		ClassNode loadLevelMenu = null;
	methods:
		for (MethodNode m : minecraft.methods) {
			AbstractInsnNode[] insns = fill(getFirst(m.instructions), 10);
			if (compareInsn(insns[0], ALOAD, 0) &&
				compareInsn(insns[1], GETFIELD, minecraft.name) &&
				compareInsn(insns[2], IFNULL) &&
				compareInsn(insns[3], RETURN) &&
				compareInsn(insns[4], ALOAD, 0) &&
				compareInsn(insns[5], NEW) &&
				compareInsn(insns[6], DUP) &&
				compareInsn(insns[7], INVOKESPECIAL, null, "<init>", "()V") &&
				compareInsn(insns[8], INVOKEVIRTUAL) &&
				compareInsn(insns[9], RETURN)) {
				ClassNode pauseMenu = source.getClass(((TypeInsnNode)insns[5]).desc);
				if (pauseMenu == null) {
					return false;
				}
				for (MethodNode m2 : pauseMenu.methods) {
					if (!m2.desc.equals("()V")) {
						continue;
					}
					int found = 0;
					AbstractInsnNode insn = getFirst(m2.instructions);
					AbstractInsnNode[] insns2 = fill(insn, 3);
					for (; insn != null; insn = nextInsn(insn)) {
						insns2 = fill(insn, 7);
						if (compareInsn(insns2[0], ALOAD) &&
							compareInsn(insns2[1], GETFIELD, null, null, "Ljava/util/List;") && (compareInsn(insns2[2], ICONST_2) || compareInsn(insns2[2], ICONST_3)) &&
							compareInsn(insns2[3], INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;") &&
							compareInsn(insns2[4], ICONST_0) &&
							compareInsn(insns2[5], PUTFIELD, null, null, "Z")) {
							m2.instructions.set(insns2[4], new InsnNode(ICONST_1));
							found = intValue(insns2[2]);
						}
						if (found == 3) {
							source.overrideClass(pauseMenu);
							break;
						}
					}
				}
				for (MethodNode m2 : pauseMenu.methods) {
					if (m2.desc.equals("()V")) {
						continue;
					}
					AbstractInsnNode insn = getFirst(m2.instructions);
					while (insn != null) {
						AbstractInsnNode[] insns2 = fill(insn, 4);
						if (compareInsn(insns2[0], ALOAD, 1) &&
							compareInsn(insns2[1], GETFIELD, null, null, "I") &&
							compareInsn(insns2[3], IF_ICMPNE)) {
							AbstractInsnNode[] insns3 = fill(nextInsn(insns2[3]), 3);
							if (compareInsn(insns3[2], NEW)) {
								if (compareInsn(insns2[2], ICONST_2)) { // Button id
									saveLevelMenu = source.getClass(((TypeInsnNode)insns3[2]).desc);
								} else if (compareInsn(insns2[2], ICONST_3)) {
									loadLevelMenu = source.getClass(((TypeInsnNode)insns3[2]).desc);
								}
							}
						}
						if (saveLevelMenu != null && loadLevelMenu != null) {
							break methods;
						}
						insn = nextInsn(insn);
					}
				}
			}
		}
		if (saveLevelMenu == null || loadLevelMenu == null) {
			return false;
		}
		ClassNode nameLevelMenu = null;
		MethodNode openFileLoad = null;
		for (MethodNode m : loadLevelMenu.methods) {
			if (openFileLoad == null && m.desc.equals("(Ljava/io/File;)V")) {
				openFileLoad = m;
				break;
			}
		}
		if (openFileLoad == null) {
			return false;
		}
		for (MethodNode m : loadLevelMenu.methods) {
			if (m.desc.equals("(I)V")) {
				InsnList insns = new InsnList();
				insns.add(new VarInsnNode(ALOAD, 0));
				insns.add(new VarInsnNode(ILOAD, 1));
				insns.add(new MethodInsnNode(INVOKESTATIC, "org/mcphackers/launchwrapper/tweak/injection/legacy/IndevSaving", "getLevelFile", "(I)Ljava/io/File;"));
				insns.add(new MethodInsnNode(INVOKEVIRTUAL, loadLevelMenu.name, openFileLoad.name, openFileLoad.desc));
				m.instructions.insert(insns);
				break;
			}
		}
		MethodNode openFile = null;
		for (MethodNode m : saveLevelMenu.methods) {
			if (m.desc.equals("(I)V")) {
				for (AbstractInsnNode insn : m.instructions) {
					if (insn.getOpcode() == NEW) {
						nameLevelMenu = source.getClass(((TypeInsnNode)insn).desc);
						break;
					}
				}
			}
		}
		if (nameLevelMenu == null) {
			return false;
		}
		for (MethodNode m : saveLevelMenu.methods) {
			if (openFile == null && m.desc.equals("(Ljava/io/File;)V")) {
				openFile = new MethodNode(m.access, m.name, m.desc, m.signature, null);
				m.accept(openFile);
				AbstractInsnNode insn = getFirst(openFile.instructions);
				while (insn != null) {
					if (insn.getOpcode() == GETFIELD) {
						FieldInsnNode field = (FieldInsnNode)insn;
						if (compareInsn(insn.getPrevious(), ALOAD, 0)) {
							FieldInsnNode getField = new FieldInsnNode(GETFIELD, nameLevelMenu.name, field.name, field.desc);
							openFile.instructions.set(insn, getField);
							insn = getField;
						}
					}
					insn = nextInsn(insn);
				}
				break;
			}
		}
		if (openFile == null) {
			return false;
		}
		FieldInsnNode idField = null;
		for (MethodNode m : nameLevelMenu.methods) {
			Type[] types = Type.getArgumentTypes(m.desc);
			if (m.name.equals("<init>") && types.length > 0 && types[types.length - 1].getDescriptor().equals("I")) {
				AbstractInsnNode insn = getFirst(m.instructions);
				while (insn != null) {
					AbstractInsnNode[] insns = fill(insn, 2);
					if (compareInsn(insns[0], ILOAD, 3) &&
						compareInsn(insns[1], PUTFIELD)) {
						idField = (FieldInsnNode)insns[1];
					}
					insn = nextInsn(insn);
				}
			}
		}
		if (idField == null) {
			return false;
		}
		for (MethodNode m : nameLevelMenu.methods) {
			if (Type.getArgumentTypes(m.desc).length == 1) {
				AbstractInsnNode insn = getFirst(m.instructions);
				while (insn != null) {
					AbstractInsnNode[] insns2 = fill(insn, 4);
					if (compareInsn(insns2[0], ALOAD, 0) &&
						compareInsn(insns2[1], GETFIELD) &&
						compareInsn(insns2[2], INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;") &&
						compareInsn(insns2[3], POP)) {
						m.instructions.remove(insns2[3]);
						InsnList before = new InsnList();
						before.add(new VarInsnNode(ALOAD, 0));
						before.add(new VarInsnNode(ALOAD, 0));
						before.add(new FieldInsnNode(GETFIELD, idField.owner, idField.name, idField.desc));
						m.instructions.insertBefore(insn, before);
						InsnList after = new InsnList();
						after.add(new MethodInsnNode(INVOKESTATIC, "org/mcphackers/launchwrapper/tweak/injection/legacy/IndevSaving", "saveLevel", "(ILjava/lang/String;)Ljava/io/File;"));
						after.add(new MethodInsnNode(INVOKEVIRTUAL, nameLevelMenu.name, openFile.name, openFile.desc));
						m.instructions.insert(insns2[2], after);
					}
					insn = nextInsn(insn);
				}
			}
		}
		LabelNode label = new LabelNode();
		InsnList insns = new InsnList();
		insns.add(new VarInsnNode(ALOAD, 1));
		insns.add(new JumpInsnNode(IFNONNULL, label));
		insns.add(new InsnNode(RETURN));
		insns.add(label);
		openFile.instructions.insert(insns);
		nameLevelMenu.methods.add(openFile);
		source.overrideClass(loadLevelMenu);
		source.overrideClass(nameLevelMenu);
		return true;
	}

	/**
	 * Used in level load screen
	 */
	public static File saveLevel(int index, String name) {
		File levelsDir = Launch.getInstance().config.levelsDir.get();
		String[] levels;
		try {
			levels = SaveRequests.getLevelNames(levelsDir);
			levels[index] = name;
			SaveRequests.updateLevelNames(levelsDir, levels);
		} catch (IOException e) {
			Launch.LOGGER.logErr("Failed to save", e);
		}
		if (name.equals(SaveRequests.EMPTY_LEVEL_ALT)) {
			File f = getLevelFile(index);
			if (f.exists()) {
				f.delete();
			}
			return null;
		}
		return getLevelFile(index);
	}

	/**
	 * Used in level save screen
	 */
	public static File getLevelFile(int index) {
		return new File(Launch.getInstance().config.levelsDir.get(), "level" + index + ".dat");
	}
}
