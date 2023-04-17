package com.vimworldedit;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.*;

import java.util.*;

public class ExampleModClient implements ClientModInitializer {
    GLFWKeyCallback oldKeyCallback;
    private static ArrayList<Action> actions = new ArrayList<>();
    private static Map<Integer, String> masks = new HashMap<>();

    /* Keys only work when this is toggled*/
    private static boolean command_mode = false;

    private static final int key_toggle_command_mode = GLFW.GLFW_KEY_GRAVE_ACCENT;
    private static final int key_zero = GLFW.GLFW_KEY_0;
    private static final int key_nine = GLFW.GLFW_KEY_9;
    private static final int key_f1 = GLFW.GLFW_KEY_F1;
    private static final int key_f12 = GLFW.GLFW_KEY_F12;

    Command command = new Command();
    Stack<Command> previous_commands = new Stack<>();

    private static void initialize_commands() {
        //commands
        actions.add(new Action("//expand", 0, GLFW.GLFW_KEY_E, 0));
        actions.add(new Action("//contract", 0, GLFW.GLFW_KEY_C, 0));
        actions.add(new Action("//move", 0, GLFW.GLFW_KEY_M, 0));
        actions.add(new Action("//stack", 0, GLFW.GLFW_KEY_S, 0));
        actions.add(new Action("//copy", 0, GLFW.GLFW_KEY_Y, 0));
        actions.add(new Action("//paste", 0, GLFW.GLFW_KEY_P, 0));
        actions.add(new Action("//flip", 0, GLFW.GLFW_KEY_F, 0));
        actions.add(new Action("//undo", 0, GLFW.GLFW_KEY_U, 0));
        actions.add(new Action("//redo", 0, GLFW.GLFW_KEY_U, 1));
        actions.add(new Action("//set 0", 0, GLFW.GLFW_KEY_D, 0));
        actions.add(new Action("//rotate", 0, GLFW.GLFW_KEY_R, 0));

        //directions
        actions.add(new Action("l", 1, GLFW.GLFW_KEY_H, 0));
        actions.add(new Action("d", 1, GLFW.GLFW_KEY_J, 0));
        actions.add(new Action("u", 1, GLFW.GLFW_KEY_K, 0));
        actions.add(new Action("r", 1, GLFW.GLFW_KEY_L, 0));
        actions.add(new Action("b", 1, GLFW.GLFW_KEY_J, 1));
        actions.add(new Action("f", 1, GLFW.GLFW_KEY_K, 1));

        //flags
        actions.add(new Action("-e", 2, GLFW.GLFW_KEY_E, 2));
        actions.add(new Action("-s", 2, GLFW.GLFW_KEY_S, 2));
        actions.add(new Action("-a", 2, GLFW.GLFW_KEY_A, 2));
        actions.add(new Action("-m", 2, GLFW.GLFW_KEY_M, 2));

        for (int i = key_f1; i <= key_f12; ++i) {
            masks.put(i, "");
        }

        Collections.sort(actions);
    }

    private static int get_action(int key, int mod) {
        Action a = new Action("", -1, key, mod);
        return Collections.binarySearch(actions, a);
    }

    private static boolean is_key_a_number(int key) {
        return key_zero <= key && key <= key_nine;
    }

    private static boolean is_key_a_function_key(int key) {
        return key_f1 <= key && key <= key_f12;
    }

    private static void toggle_command_mode() {
        command_mode = !command_mode;

        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal(command_mode ? "Vimworldedit: on" : "Vimworldedit: off"));
        }
    }

    private void update_command(int key, int mod) {
        if (is_key_a_number(key)) {
            final int number = key - key_zero;

            if (number == 0 && command.number.length() == 0) {
                return;
            }
            command.number += Integer.toString(number);
            return;
        }

        if (is_key_a_function_key(key)) {
            if (mod == 0) {
                command.mask = masks.get(key);
            } else if (mod == GLFW.GLFW_MOD_ALT) {
                command.mask = "!" + masks.get(key);
            } else {
                var client = MinecraftClient.getInstance();
                if (client == null) {
                    return;
                }
                var player = client.player;

                if (player == null) {
                    return;
                }

                if (mod == (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT)) {
                    player.sendMessage(Text.literal(masks.get(key)));
                    return;
                }

                HitResult hitResult = player.raycast(30.0, 0.f, false);
                if (hitResult.getType() != HitResult.Type.BLOCK) {
                    player.sendMessage(Text.literal("No block in sight!"));
                    return;
                }

                BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                ClientPlayNetworkHandler nhandler = client.getNetworkHandler();
                if (nhandler == null) {
                    player.sendMessage(Text.literal("Could not obtain network handler."));
                    return;
                }

                if (nhandler.getWorld() != null) {
                    Block block = nhandler.getWorld().getBlockState(blockHitResult.getBlockPos()).getBlock();
                    var name = block.getName().toString();
                    var last_dot_idx = name.lastIndexOf('.');
                    name = name.substring(last_dot_idx + 1, name.indexOf('\'', last_dot_idx));

                    if (mod == GLFW.GLFW_MOD_CONTROL) {
                        masks.put(key, name);
                    } else if (mod == GLFW.GLFW_MOD_SHIFT) {
                        masks.put(key, masks.get(key) + "," + name);
                    }

                }
            }

            return;
        }

        int actionidx = get_action(key, mod);

        if (actionidx >= actions.size() || actionidx < 0) {
            return;
        }

        Action action = actions.get(actionidx);
        if ((action.glfw_mod == mod && action.glfw_key == key) == false) {
            return;
        }


        switch (action.category) {
            case 0: {
                command.command = action.command;
                execute_command();
                break;
            }
            case 1: {
                final String delim = command.directions.length() == 0 ? "" : ",";
                command.directions += delim + action.command;
                break;
            }

            case 2: {
                final String delim = command.flags.length() == 0 ? "" : " ";
                String flag = action.command;

                // a mask flag should be at the end of a command.
                if (flag.equals("-m")) {
                    command.flags = command.flags + delim + flag;
                } else {
                    command.flags = flag + delim + command.flags;
                }

                break;
            }
        }
    }

    private void execute_command() {
        if (MinecraftClient.getInstance().getNetworkHandler() == null) {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(Text.literal("You are not on a server with WorldEdit plugin installed!"));
            }
            return;
        }

        if ((command.command.equals("//undo") || command.command.equals("//redo")) == false) {
            previous_commands.add(command.clone());
            if (previous_commands.size() > 10) {
                previous_commands.remove(0);
            }
        }

        String cmd = command.toString();
        command.clear();

        if (cmd.endsWith("-m")) {
            final long window = MinecraftClient.getInstance().getWindow().getHandle();
            GLFW.glfwSetClipboardString(window, cmd + " ");
            oldKeyCallback.invoke(window, 84, 20, 1, 0);
            oldKeyCallback.invoke(window, 84, 20, 0, 0);
            command_mode = false;
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(Text.literal("Paste the command in the chat now."));
            }
            return;
        }

        cmd = cmd.substring(1);
        cmd = cmd.trim();
        MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(cmd);
    }

    private void handle_keys(long window, int key, int scancode, int action, int modifier) {
        if (key == key_toggle_command_mode && action == GLFW.GLFW_PRESS) {
            toggle_command_mode();
        }

        if (command_mode == false) {
            oldKeyCallback.invoke(window, key, scancode, action, modifier);
            return;
        }

        if (action != GLFW.GLFW_PRESS) {
            return;
        }

        if (key == GLFW.GLFW_KEY_PERIOD) {
            int cmds_to_repeat = 1;
            Stack<Command> repeat_stack = new Stack<>();
            if (command.number.isEmpty() == false) {
                cmds_to_repeat = Integer.parseInt(command.number);
                cmds_to_repeat = Math.min(previous_commands.size(), cmds_to_repeat);
            }

            for (int i = 0; i < cmds_to_repeat; ++i) {
                repeat_stack.add(previous_commands.peek().clone());
                previous_commands.pop();
            }

            for (int i = 0; i < cmds_to_repeat; ++i) {
                command = repeat_stack.peek();
                repeat_stack.pop();
                execute_command();
            }
            return;
        }


        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (command.toString().trim().isEmpty()) {
                toggle_command_mode();
                return;
            }

            command.clear();
            return;
        }

        update_command(key, modifier);

    }

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            initialize_commands();

            var keyCallback = new GLFWKeyCallback() {
                @Override
                public void invoke(long window, int key, int scancode, int action, int mods) {
                    handle_keys(window, key, scancode, action, mods);
                }
            };

            oldKeyCallback = GLFW.glfwSetKeyCallback(MinecraftClient.getInstance().getWindow().getHandle(), keyCallback);
        });
    }
}