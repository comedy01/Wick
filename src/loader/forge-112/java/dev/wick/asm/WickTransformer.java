package dev.wick.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class WickTransformer implements IClassTransformer {
    private static final Logger LOGGER = LogManager.getLogger("wick");
    private static final String DESC = "(Lnet/minecraft/util/math/BlockPos;I)I";
    private static final String HOOK = "dev/wick/client/LightHook";
    private static final String HOOK_DESC = "(ILnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)I";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !("net.minecraft.world.World".equals(transformedName)
                || "net.minecraft.world.ChunkCache".equals(transformedName))) {
            return basicClass;
        }
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        int patched = 0;
        for (MethodNode method : node.methods) {
            if (DESC.equals(method.desc) && ("getCombinedLight".equals(method.name) || "func_175626_b".equals(method.name))) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.IRETURN) {
                        InsnList hook = new InsnList();
                        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "combined", HOOK_DESC, false));
                        method.instructions.insertBefore(insn, hook);
                        patched++;
                    }
                }
            }
        }
        if (patched == 0) {
            LOGGER.warn("Wick found no light lookup to hook in {}", transformedName);
            return basicClass;
        }
        LOGGER.info("Wick hooked the light lookup in {}", transformedName);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
