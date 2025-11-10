package exe.example.miSidetext.managers;

import exe.example.miSidetext.MiSidetext;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final MiSidetext plugin;

    public CommandManager(MiSidetext plugin) {
        this.plugin = plugin;
        // 延迟注册命令，避免构造函数中泄漏this
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                plugin.getCommand("misidetext").setExecutor(CommandManager.this);
                plugin.getCommand("st").setExecutor(CommandManager.this); // 快捷命令
                
                // 注册TabCompleter
                plugin.getCommand("misidetext").setTabCompleter(CommandManager.this);
                plugin.getCommand("st").setTabCompleter(CommandManager.this);
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 只有玩家可以使用某些命令
        if (!(sender instanceof Player) && args.length > 0 && !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§c只有玩家可以使用此命令！");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "toggle":
                handleToggleCommand((Player) sender);
                break;
            case "reload":
                handleReloadCommand(sender);
                break;
            case "test":
                handleTestCommand((Player) sender);
                break;
            case "settings":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /st settings <typing/bounce/fall> <value>");
                    return true;
                }
                handleSettingsCommand((Player) sender, args[1], args.length > 2 ? args[2] : null);
                break;
            case "channel":
                handleChannelCommand((Player) sender, args.length > 1 ? args[1] : null);
                break;
            case "performance":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /st performance <start/status/stop> [参数]");
                    return true;
                }
                handlePerformanceCommand((Player) sender, args[1], args);
                break;
            default:
                sendHelpMessage(sender);
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<String>();
        
        if (args.length == 1) {
            // 主命令补全
            List<String> mainCommands = Arrays.asList("toggle", "reload", "test", "settings", "channel", "performance");
            for (String cmd : mainCommands) {
                if (cmd.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            // 子命令补全
            switch (args[0].toLowerCase()) {
                case "settings":
                    List<String> settings = Arrays.asList("typing", "bounce", "fall", "direction", "status");
                    for (String setting : settings) {
                        if (setting.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(setting);
                        }
                    }
                    break;
                case "channel":
                    List<String> channels = Arrays.asList("normal", "shout", "whisper", "global");
                    for (String channel : channels) {
                        if (channel.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(channel);
                        }
                    }
                    break;
                case "performance":
                    List<String> perfCommands = Arrays.asList("start", "status", "stop");
                    for (String cmd : perfCommands) {
                        if (cmd.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(cmd);
                        }
                    }
                    break;
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("settings") && args[1].equalsIgnoreCase("direction")) {
            // direction命令的补全
            List<String> directions = Arrays.asList("left", "right", "toggle");
            for (String direction : directions) {
                if (direction.toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(direction);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("performance") && args[1].equalsIgnoreCase("start")) {
            // 性能测试持续时间提示
            completions.add("<持续时间(秒)>");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("performance") && args[1].equalsIgnoreCase("start")) {
            // 性能测试频率提示
            completions.add("<频率(tick)>");
        } else if (args.length == 5 && args[0].equalsIgnoreCase("performance") && args[1].equalsIgnoreCase("start")) {
            // 性能测试文本长度提示
            completions.add("<文本长度>");
        }
        
        return completions;
    }

    /**
     * 发送帮助信息
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6===== MiSidetext 帮助 =====");
        sender.sendMessage("§e/st toggle - §f开关侧边文本效果");
        sender.sendMessage("§e/st reload - §f重载插件配置");
        sender.sendMessage("§e/st test - §f测试侧边文本效果");
        sender.sendMessage("§e/st settings <typing/bounce/fall/direction> <value> - §f调整动画设置");
        sender.sendMessage("§e/st settings direction <left|right|toggle> - §f切换阅读方向");
        sender.sendMessage("§e/st channel <频道> - §f设置默认聊天频道 (normal/shout/whisper/global)");
        sender.sendMessage("§e/st performance start <持续时间> <频率> <文本长度> - §f开始性能测试");
        sender.sendMessage("§e/st performance status - §f查看测试状态和TPS");
        sender.sendMessage("§e/st performance stop - §f停止性能测试");
        sender.sendMessage("§6=========================");
    }

    /**
     * 处理开关命令
     */
    private void handleToggleCommand(Player player) {
        boolean enabled = plugin.getPlayerPreferencesManager().togglePlayerEnabled(player);
        
        String status = enabled ? "§a启用" : "§c禁用";
        player.sendMessage("§6[MiSidetext] §f您的侧边文本效果已" + status + "！");
    }

    /**
     * 处理重载命令
     */
    private void handleReloadCommand(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage("§6[MiSidetext] §f配置已成功重载！");
    }

    /**
     * 处理测试命令
     */
    private void handleTestCommand(Player player) {
        plugin.getSideTextManager().displaySideText(player, "这是一条测试消息！");
        player.sendMessage("§6[MiSidetext] §f测试消息已显示！");
    }

    /**
     * 检查玩家是否有权限修改设置
     */
    private boolean hasSettingsPermission(Player player) {
        return player.hasPermission("misidetext.settings") || player.isOp();
    }

    /**
     * 处理设置命令
     */
    private void handleSettingsCommand(Player player, String settingType, String value) {
        // 检查权限
        if (!hasSettingsPermission(player)) {
            player.sendMessage("§c您没有权限修改这些设置！");
            return;
        }

        switch (settingType.toLowerCase()) {
            case "typing":
                if (value != null) {
                    try {
                        long speed = Long.parseLong(value);
                        // 限制范围
                        if (speed < 50 || speed > 500) {
                            player.sendMessage("§c打字速度必须在50-500ms之间！");
                            return;
                        }
                        plugin.getPlayerPreferencesManager().setTypingSpeed(player, speed);
                        player.sendMessage("§6[MiSidetext] §f您的打字速度已设置为: " + speed + "ms");
                    } catch (NumberFormatException e) {
                        player.sendMessage("§c请输入有效的数字！");
                    }
                } else {
                    long current = plugin.getPlayerPreferencesManager().getTypingSpeed(player);
                    player.sendMessage("§6[MiSidetext] §f您当前的打字速度: " + current + "ms");
                }
                break;
            case "bounce":
                if (value != null) {
                    try {
                        double height = Double.parseDouble(value);
                        // 限制范围
                        if (height < 0 || height > 0.5) {
                            player.sendMessage("§c跳动高度必须在0-0.5之间！");
                            return;
                        }
                        plugin.getPlayerPreferencesManager().setBounceHeight(player, height);
                        player.sendMessage("§6[MiSidetext] §f您的跳动高度已设置为: " + height);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§c请输入有效的数字！");
                    }
                } else {
                    double current = plugin.getPlayerPreferencesManager().getBounceHeight(player);
                    player.sendMessage("§6[MiSidetext] §f您当前的跳动高度: " + current);
                }
                break;
            case "fall":
                if (value != null) {
                    try {
                        double duration = Double.parseDouble(value);
                        // 限制范围
                        if (duration < 1 || duration > 10) {
                            player.sendMessage("§c掉落持续时间必须在1-10秒之间！");
                            return;
                        }
                        plugin.getPlayerPreferencesManager().setFallDuration(player, duration);
                        player.sendMessage("§6[MiSidetext] §f您的掉落持续时间已设置为: " + duration + "秒");
                    } catch (NumberFormatException e) {
                        player.sendMessage("§c请输入有效的数字！");
                    }
                } else {
                    double current = plugin.getPlayerPreferencesManager().getFallDuration(player);
                    player.sendMessage("§6[MiSidetext] §f您当前的掉落持续时间: " + current + "秒");
                }
                break;
            case "direction":
                if (value != null) {
                    boolean leftToRight;
                    if (value.equalsIgnoreCase("left")) {
                        leftToRight = true;
                    } else if (value.equalsIgnoreCase("right")) {
                        leftToRight = false;
                    } else if (value.equalsIgnoreCase("toggle")) {
                        leftToRight = plugin.getPlayerPreferencesManager().toggleReadingDirection(player);
                    } else {
                        player.sendMessage("§c用法: /st settings direction <left|right|toggle>");
                        return;
                    }
                    if (!value.equalsIgnoreCase("toggle")) {
                        plugin.getPlayerPreferencesManager().setReadingDirection(player, leftToRight);
                    }
                    String direction = leftToRight ? "从左到右" : "从右到左";
                    player.sendMessage("§6[MiSidetext] §f您的阅读方向已设置为: " + direction);
                } else {
                    boolean current = plugin.getPlayerPreferencesManager().isLeftToRight(player);
                    String direction = current ? "从左到右" : "从右到左";
                    player.sendMessage("§6[MiSidetext] §f您当前的阅读方向: " + direction);
                    player.sendMessage("§e使用 /st settings direction toggle 可以切换方向");
                }
                break;
            case "status":
                boolean enabled = plugin.getPlayerPreferencesManager().isPlayerEnabled(player);
                String status = enabled ? "§a已启用" : "§c已禁用";
                player.sendMessage("§6[MiSidetext] §f您的侧边文本状态: " + status);
                break;
            default:
                player.sendMessage("§c无效的设置类型！可用: typing, bounce, fall, status");
        }
    }
    
    /**
     * 处理设置默认聊天频道命令
     */
    private void handleChannelCommand(Player player, String channelType) {
        if (channelType == null) {
            player.sendMessage("§c用法: /st channel <频道>");
            player.sendMessage("§e可用频道: normal, shout, whisper, global");
            return;
        }
        
        ChatManager.ChatChannel channel = null;
        if (channelType.toLowerCase().equals("shout")) {
            channel = ChatManager.ChatChannel.SHOUT;
        } else if (channelType.toLowerCase().equals("whisper")) {
            channel = ChatManager.ChatChannel.WHISPER;
        } else if (channelType.toLowerCase().equals("global")) {
            channel = ChatManager.ChatChannel.GLOBAL;
        } else {
            channel = ChatManager.ChatChannel.NORMAL;
        }
        
        plugin.getPlayerPreferencesManager().setDefaultChatChannel(player, channel);
        player.sendMessage("§6[MiSidetext] §f默认聊天频道已设置为: " + getChannelName(channel));
    }
    
    /**
     * 获取聊天频道的中文名称
     */
    private String getChannelName(ChatManager.ChatChannel channel) {
        if (channel == ChatManager.ChatChannel.SHOUT) {
            return "大喊";
        } else if (channel == ChatManager.ChatChannel.WHISPER) {
            return "小声";
        } else if (channel == ChatManager.ChatChannel.GLOBAL) {
            return "全服";
        } else {
            return "普通";
        }
    }
    
    /**
     * 处理性能测试命令
     */
    private void handlePerformanceCommand(Player player, String subCommand, String[] args) {
        // 只有OP或有权限的玩家才能使用性能测试功能
        if (!player.isOp() && !player.hasPermission("misidetext.reload")) {
            player.sendMessage("§c您没有权限使用性能测试功能！");
            return;
        }
        
        if (subCommand.toLowerCase().equals("start")) {
            if (args.length < 5) {
                player.sendMessage("§c用法: /st performance start <持续时间(秒)> <频率(tick)> <文本长度>");
                player.sendMessage("§e例如: /st performance start 30 2 20 - 测试30秒，每2tick生成一次，每次20字符");
                return;
            }
            
            try {
                int duration = Integer.parseInt(args[2]);
                int frequency = Integer.parseInt(args[3]);
                int textLength = Integer.parseInt(args[4]);
                
                // 参数验证
                if (duration < 5 || duration > 300) {
                    player.sendMessage("§c持续时间必须在5-300秒之间！");
                    return;
                }
                if (frequency < 1 || frequency > 20) {
                    player.sendMessage("§c频率必须在1-20tick之间！");
                    return;
                }
                if (textLength < 1 || textLength > 100) {
                    player.sendMessage("§c文本长度必须在1-100字符之间！");
                    return;
                }
                
                plugin.getPerformanceTestManager().startTest(player, duration, frequency, textLength);
            } catch (NumberFormatException e) {
                player.sendMessage("§c请输入有效的数字参数！");
            }
        } else if (subCommand.toLowerCase().equals("status")) {
            plugin.getPerformanceTestManager().getTestStatus(player);
        } else if (subCommand.toLowerCase().equals("stop")) {
            plugin.getPerformanceTestManager().stopTest(player);
        } else {
            player.sendMessage("§c未知的子命令！可用: start, status, stop");
        }
    }
}