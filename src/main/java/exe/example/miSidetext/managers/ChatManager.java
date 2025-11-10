package exe.example.miSidetext.managers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import exe.example.miSidetext.MiSidetext;

public class ChatManager {

    private final MiSidetext plugin;
    private final Map<UUID, List<Long>> playerMessageTimestamps; // 玩家消息时间戳
    private final Set<UUID> mutedPlayers; // 被禁言的玩家
    private final Map<UUID, Long> muteEndTimes; // 禁言结束时间
    private final File logsDirectory; // 日志目录
    
    // 配置参数
    private int spamThreshold; // 短时间内发送的消息数量阈值
    private long spamTimeWindow; // 检测时间窗口（毫秒）
    private long muteDuration; // 禁言时长（毫秒）
    private boolean chatLoggingEnabled; // 是否启用聊天记录
    
    public ChatManager(MiSidetext plugin) {
        this.plugin = plugin;
        this.playerMessageTimestamps = new ConcurrentHashMap<>();
        this.mutedPlayers = new HashSet<>();
        this.muteEndTimes = new ConcurrentHashMap<>();
        
        // 创建日志目录
        this.logsDirectory = new File(plugin.getDataFolder(), "chat_logs");
        if (!logsDirectory.exists()) {
            logsDirectory.mkdirs();
        }
        
        // 加载配置
        loadConfig();
        
        // 启动禁言检查任务
        startMuteCheckTask();
    }
    
    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        this.spamThreshold = config.getInt("chat-management.spam-threshold", 5);
        this.spamTimeWindow = config.getLong("chat-management.spam-time-window", 3000); // 3秒
        this.muteDuration = config.getLong("chat-management.mute-duration", 10) * 1000; // 默认10秒
        this.chatLoggingEnabled = config.getBoolean("chat-management.log-enabled", true);
    }
    
    /**
     * 处理玩家聊天消息，进行刷屏检测
     * @param player 玩家
     * @return 是否允许发送消息
     */
    public boolean handlePlayerChat(Player player) {
        UUID playerId = player.getUniqueId();
        
        // 检查是否被禁言
        if (isMuted(playerId)) {
            long remainingSeconds = getRemainingMuteTime(playerId);
            player.sendMessage(ChatColor.RED + "你已被禁言，请等待 " + remainingSeconds + " 秒后再发言！");
            return false;
        }
        
        // 检查刷屏
        if (checkSpam(playerId)) {
            mutePlayer(playerId);
            player.sendMessage(ChatColor.RED + "你发送消息过于频繁，已被临时禁言 " + (muteDuration / 1000) + " 秒！");
            plugin.getLogger().info(String.format("玩家 %s 因刷屏被禁言", player.getName()));
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查是否刷屏
     */
    private boolean checkSpam(UUID playerId) {
        long currentTime = System.currentTimeMillis();
        List<Long> timestamps = playerMessageTimestamps.computeIfAbsent(playerId, k -> new ArrayList<>());
        
        // 添加当前消息时间戳
        timestamps.add(currentTime);
        
        // 清理过期的时间戳
        Iterator<Long> iterator = timestamps.iterator();
        while (iterator.hasNext()) {
            if (currentTime - iterator.next() > spamTimeWindow) {
                iterator.remove();
            }
        }
        
        // 如果消息数量超过阈值，判定为刷屏
        return timestamps.size() > spamThreshold;
    }
    
    /**
     * 禁言玩家
     */
    private void mutePlayer(UUID playerId) {
        mutedPlayers.add(playerId);
        muteEndTimes.put(playerId, System.currentTimeMillis() + muteDuration);
        
        // 清理消息时间戳，避免重复触发
        playerMessageTimestamps.remove(playerId);
    }
    
    /**
     * 检查玩家是否被禁言
     */
    public boolean isMuted(UUID playerId) {
        return mutedPlayers.contains(playerId);
    }
    
    /**
     * 检查玩家是否被禁言（Player参数版本）
     */
    public boolean isPlayerMuted(Player player) {
        return isMuted(player.getUniqueId());
    }
    
    /**
     * 检查是否刷屏（Player参数版本）
     */
    public boolean isSpamming(Player player, String message) {
        return checkSpam(player.getUniqueId());
    }
    
    /**
     * 临时禁言玩家
     */
    public void mutePlayerTemporarily(Player player) {
        mutePlayer(player.getUniqueId());
    }
    
    /**
     * 获取剩余禁言时间（秒）
     */
    private long getRemainingMuteTime(UUID playerId) {
        Long endTime = muteEndTimes.get(playerId);
        if (endTime == null) return 0;
        
        long remainingMs = endTime - System.currentTimeMillis();
        return Math.max(0, remainingMs / 1000);
    }
    
    /**
     * 启动禁言检查任务，自动解除过期禁言
     */
    private void startMuteCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Long>> iterator = muteEndTimes.entrySet().iterator();
                
                while (iterator.hasNext()) {
                    Map.Entry<UUID, Long> entry = iterator.next();
                    if (currentTime > entry.getValue()) {
                        mutedPlayers.remove(entry.getKey());
                        iterator.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 每秒检查一次
    }
    
    /**
     * 保存聊天记录（Player和message参数版本）
     */
    public void logChat(Player player, String message) {
        logChat(player, message, "NORMAL");
    }
    
    /**
     * 保存聊天记录（完整版本）
     */
    public void logChat(Player player, String message, String channel) {
        if (!chatLoggingEnabled) return;
        
        try {
            // 获取当前日期的日志文件
            String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            File logFile = new File(logsDirectory, "chat_" + date + ".log");
            
            // 记录聊天内容
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            String logEntry = "[" + timestamp + "] [" + channel + "] " + player.getName() + ": " + message;
            
            // 写入文件
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(logEntry);
                writer.newLine();
            }
        } catch (IOException e) {
            plugin.getLogger().warning(String.format("保存聊天记录失败: %s", e.getMessage()));
        }
    }
    
    /**
     * 获取玩家的默认聊天频道
     */
    public ChatChannel getDefaultChannel(Player player) {
        return plugin.getPlayerPreferencesManager().getDefaultChatChannel(player);
    }
    
    /**
     * 设置玩家的默认聊天频道
     */
    public void setDefaultChannel(Player player, ChatChannel channel) {
        plugin.getPlayerPreferencesManager().setDefaultChatChannel(player, channel);
    }
    
    /**
     * 聊天频道枚举
     */
    public enum ChatChannel {
        NORMAL(50.0, 1.0, ""),
        SHOUT(100.0, 1.5, "!"),
        WHISPER(5.0, 0.7, "~"),
        GLOBAL(-1.0, -1.0, "@");
        
        private final double radius;
        private final double textScale;
        private final String prefix;
        
        ChatChannel(double radius, double textScale, String prefix) {
            this.radius = radius;
            this.textScale = textScale;
            this.prefix = prefix;
        }
        
        public double getRadius() {
            return radius;
        }
        
        public double getTextScale() {
            return textScale;
        }
        
        public String getPrefix() {
            return prefix;
        }
        
        // 通过前缀识别频道类型
        public static ChatChannel fromPrefix(String message) {
            for (ChatChannel channel : values()) {
                if (message.startsWith(channel.getPrefix()) && !channel.equals(NORMAL)) {
                    return channel;
                }
            }
            return NORMAL; // 默认返回普通聊天
        }
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        playerMessageTimestamps.clear();
        mutedPlayers.clear();
        muteEndTimes.clear();
    }
}