package org.mcphackers.launchwrapper.tweak.injection.legacy;

import static org.mcphackers.rdi.util.InsnHelper.*;
import static org.objectweb.asm.Opcodes.*;

import org.mcphackers.launchwrapper.LaunchConfig;
import org.mcphackers.launchwrapper.tweak.injection.InjectionWithContext;
import org.mcphackers.launchwrapper.util.ClassNodeSource;
import org.mcphackers.rdi.util.NodeHelper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class UnlicensedCopyText extends InjectionWithContext<LegacyTweakContext> {

    public UnlicensedCopyText(LegacyTweakContext context) {
        super(context);
    }

    @Override
    public String name() {
        return "Unlicensed Copy text";
    }

    @Override
    public boolean required() {
        return false;
    }

    @Override
    public boolean apply(ClassNodeSource source, LaunchConfig config) {
        if(config.haspaid.get()) {
            return false;
        }
        MethodNode clinit = NodeHelper.getMethod(context.minecraft, "<clinit>", "()V");
        if(clinit == null) {
            return false;
        }
        for(AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = nextInsn(insn)) {
            if(compareInsn(insn, LCONST_0) && compareInsn(nextInsn(insn), PUTSTATIC, context.minecraft.name, null, "J")) {
                clinit.instructions.set(insn, longInsn(1));
                source.overrideClass(context.minecraft);
                return true;
            }
        }
        
        return false;
    }

}
