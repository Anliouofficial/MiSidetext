package exe.example.miSidetext.listeners;

import exe.example.miSidetext.MiSidetext;
import exe.example.miSidetext.managers.ChatManager;
import exe.example.miSidetext.managers.SideTextManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class ChatListener implements Listener {

    private final MiSidetext plugin;
    private final SideTextManager sideTextManager;
    private final ChatManager chatManager;
    
    public ChatListener(MiSidetext plugin) {
        this.plugin = plugin;
        this.sideTextManager = plugin.getSideTextManager();
        this.chatManager = plugin.getChatManager();
    }
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        // 检查禁言状态
        if (chatManager.isPlayerMuted(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "你已被禁言，请等待一段时间后再发言！");
            return;
        }
        
        // 检查刷屏
        if (chatManager.isSpamming(player, message)) {
            event.setCancelled(true);
            chatManager.mutePlayerTemporarily(player);
            player.sendMessage(ChatColor.RED + "你发送消息过于频繁，已被临时禁言！");
            plugin.getLogger().info(String.format("玩家 %s 因刷屏被禁言", player.getName()));
            return;
        }
        
        // 确定聊天频道
        ChatManager.ChatChannel channel = determineChatChannel(player, message);
        String originalMessage = message;
        
        // 处理频道特定逻辑
        if (channel != ChatManager.ChatChannel.NORMAL && originalMessage.startsWith(channel.getPrefix())) {
            // 移除前缀
            message = message.substring(channel.getPrefix().length()).trim();
        }
        
        // 添加频道前缀显示
        String channelPrefix = getChannelPrefix(channel);
        String formattedMessage = channelPrefix + player.getDisplayName() + ChatColor.WHITE + ": " + message;
        
        // 根据频道调整接收者范围
        if (channel != ChatManager.ChatChannel.GLOBAL) {
            List<Player> recipients = new ArrayList<>();
            double radius = channel.getRadius();
            
            // 收集范围内的玩家
            for (Player recipient : Bukkit.getOnlinePlayers()) {
                if (recipient.getWorld().equals(player.getWorld()) &&
                    recipient.getLocation().distance(player.getLocation()) <= radius) {
                    recipients.add(recipient);
                }
            }
            
            event.getRecipients().clear();
            event.getRecipients().addAll(recipients);
        }
        
        // 设置消息格式
        event.setFormat(formattedMessage);
        
        // 保存聊天记录
        chatManager.logChat(player, message, channel.name());
        
        // 添加侧边文本效果
        final String finalMessage = message;
        final double textScale = channel.getTextScale();
        
        new BukkitRunnable() {
            @Override
            public void run() {
                // 在主线程中执行
                sideTextManager.displaySideText(player, finalMessage, textScale);
            }
        }.runTask(plugin);
    }
    
    /**
     * 确定玩家使用的聊天频道
     */
    private ChatManager.ChatChannel determineChatChannel(Player player, String message) {
        // 首先检查消息前缀
        ChatManager.ChatChannel channel = ChatManager.ChatChannel.fromPrefix(message);
        
        // 如果不是通过前缀识别的特殊频道，则使用玩家的默认频道
        if (channel == ChatManager.ChatChannel.NORMAL) {
            channel = chatManager.getDefaultChannel(player);
        }
        
        return channel;
    }
    
    /**
     * 获取频道前缀
     */
    private String getChannelPrefix(ChatManager.ChatChannel channel) {
        return switch (channel) {
            case SHOUT -> ChatColor.RED + "[大喊] " + ChatColor.WHITE;
            case WHISPER -> ChatColor.GRAY + "[小声] " + ChatColor.WHITE;
            case GLOBAL -> ChatColor.GOLD + "[全局] " + ChatColor.WHITE;
            default -> "";
        };
    }
}