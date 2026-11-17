// Forge 47's Container distance helper is a static interface method. The bundled
// Mixin 0.8.5 cannot inject into interfaces, so use Forge's native coremod API.
function initializeCoreMod() {
    var Opcodes = Java.type('org.objectweb.asm.Opcodes');
    var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
    var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
    var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    return {
        'enderface_container_access': {
            'target': {
                'type': 'METHOD',
                'class': 'net.minecraft.world.Container',
                // This double overload is Forge-added and has no obfuscated name.
                'methodName': 'stillValidBlockEntity',
                'methodDesc': '(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/player/Player;D)Z'
            },
            'transformer': function(method) {
                var instructions = method.instructions.toArray();
                for (var i = 0; i < instructions.length; i++) {
                    if (instructions[i].getOpcode() === Opcodes.IRETURN) {
                        var hook = new InsnList();
                        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            'com/enderio/enderio/content/enderface/EnderfaceMenuAccess', 'stillValid',
                            '(ZLnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/player/Player;)Z', false));
                        method.instructions.insertBefore(instructions[i], hook);
                    }
                }
                method.maxStack = Math.max(method.maxStack, 3);
                return method;
            }
        }
    };
}
