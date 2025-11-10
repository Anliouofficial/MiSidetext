package exe.example.miSidetext.managers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import exe.example.miSidetext.MiSidetext;

public class PlayerPreferencesManager {

    private final MiSidetext plugin;
    private final Map<UUID, YamlConfiguration> playerPreferences = new HashMap<>();
    private final File preferencesFolder;

    public PlayerPreferencesManager(MiSidetext plugin) {
        this.plugin = plugin;
        this.preferencesFolder = new File(plugin.getDataFolder(), "preferences");
        if (!preferencesFolder.exists()) {
            preferencesFolder.mkdirs();
        }
    }

    /**
     * 获取玩家的偏好设置
     */
    public YamlConfiguration getPlayerPreferences(Player player) {
        if (player == null) {
            plugin.getLogger().warning("尝试获取空玩家的偏好设置");
            return new YamlConfiguration(); // 返回空配置以避免空指针
        }
        
        UUID playerId = player.getUniqueId();
        
        if (!playerPreferences.containsKey(playerId)) {
            loadPlayerPreferences(player);
        }
        
        return playerPreferences.get(playerId);
    }

    /**
     * 加载玩家偏好设置
     */
    private void loadPlayerPreferences(Player player) {
        if (player == null) {
            plugin.getLogger().warning("尝试加载空玩家的偏好设置");
            return;
        }
        
        UUID playerId = player.getUniqueId();
        File playerFile = new File(preferencesFolder, playerId + ".yml");
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
        
        // 设置默认值
        if (!config.contains("enabled")) {
            config.set("enabled", true);
        }
        if (!config.contains("typing-speed")) {
            config.set("typing-speed", plugin.getConfig().getLong("side-text.typing-speed"));
        }
        if (!config.contains("bounce-height")) {
            config.set("bounce-height", plugin.getConfig().getDouble("side-text.bounce-height"));
        }
        if (!config.contains("fall-duration")) {
            config.set("fall-duration", plugin.getConfig().getDouble("side-text.fall-duration"));
        }
        // 设置默认聊天频道
        if (!config.contains("default-channel")) {
            config.set("default-channel", "NORMAL");
        }
        
        playerPreferences.put(playerId, config);
    }

    /**
     * 保存玩家偏好设置
     */
    public void savePlayerPreferences(Player player) {
        UUID playerId = player.getUniqueId();
        
        if (playerPreferences.containsKey(playerId)) {
            File playerFile = new File(preferencesFolder, playerId + ".yml");
            
            try {
                playerPreferences.get(playerId).save(playerFile);
            } catch (IOException e) {
                plugin.getLogger().warning(String.format("无法保存玩家 %s 的偏好设置！", player.getName()));
                e.printStackTrace();
            }
        }
    }

    /**
     * 切换玩家的侧边文本启用状态
     */
    public boolean togglePlayerEnabled(Player player) {
        YamlConfiguration config = getPlayerPreferences(player);
        boolean enabled = !config.getBoolean("enabled");
        config.set("enabled", enabled);
        savePlayerPreferences(player);
        return enabled;
    }

    /**
     * 检查玩家的侧边文本是否启用
     */
    public boolean isPlayerEnabled(Player player) {
        if (player == null) {
            return false;
        }
        // 如果全局禁用，直接返回false
        if (!plugin.getConfig().getBoolean("side-text.enabled", true)) {
            return false;
        }
        return getPlayerPreferences(player).getBoolean("enabled", true);
    }

    /**
     * 设置玩家的打字速度
     */
    public void setTypingSpeed(Player player, long speed) {
        YamlConfiguration config = getPlayerPreferences(player);
        config.set("typing-speed", speed);
        savePlayerPreferences(player);
    }

    /**
     * 设置玩家的跳动高度
     */
    public void setBounceHeight(Player player, double height) {
        YamlConfiguration config = getPlayerPreferences(player);
        config.set("bounce-height", height);
        savePlayerPreferences(player);
    }

    /**
     * 设置玩家的掉落持续时间
     */
    public void setFallDuration(Player player, double duration) {
        YamlConfiguration config = getPlayerPreferences(player);
        config.set("fall-duration", duration);
        savePlayerPreferences(player);
    }

    /**
     * 获取玩家的打字速度
     */
    public long getTypingSpeed(Player player) {
        return getPlayerPreferences(player).getLong("typing-speed");
    }

    /**
     * 获取玩家的跳动高度
     */
    public double getBounceHeight(Player player) {
        return getPlayerPreferences(player).getDouble("bounce-height");
    }

    /**
     * 获取玩家的掉落持续时间
     */
    public double getFallDuration(Player player) {
        return getPlayerPreferences(player).getDouble("fall-duration");
    }
    
    /**
     * 获取玩家的默认聊天频道
     */
    public ChatManager.ChatChannel getDefaultChatChannel(Player player) {
        try {
            String channelName = getPlayerPreferences(player).getString("default-channel", "NORMAL");
            return ChatManager.ChatChannel.valueOf(channelName);
        } catch (IllegalArgumentException e) {
            // 如果通道名称无效，返回默认通道
            return ChatManager.ChatChannel.NORMAL;
        }
    }
    
    /**
     * 设置玩家的默认聊天频道
     */
    public void setDefaultChatChannel(Player player, ChatManager.ChatChannel channel) {
        YamlConfiguration config = getPlayerPreferences(player);
        config.set("default-channel", channel.name());
        savePlayerPreferences(player);
    }

    /**
     * 清理所有缓存的玩家偏好设置
     */
    public void cleanup() {
        playerPreferences.clear();
    }
}