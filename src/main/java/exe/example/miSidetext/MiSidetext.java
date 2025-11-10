package exe.example.miSidetext;

import exe.example.miSidetext.listeners.ChatListener;
import exe.example.miSidetext.managers.ChatManager;
import exe.example.miSidetext.managers.CommandManager;
import exe.example.miSidetext.managers.PerformanceTestManager;
import exe.example.miSidetext.managers.PlayerPreferencesManager;
import exe.example.miSidetext.managers.SideTextManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class MiSidetext extends JavaPlugin {

    private static MiSidetext instance;
    private SideTextManager sideTextManager;
    private PlayerPreferencesManager playerPreferencesManager;
    private ChatManager chatManager;
    private PerformanceTestManager performanceTestManager;

    @Override
    public void onEnable() {
        // 保存插件实例
        instance = this;
        
        // 保存默认配置
        saveDefaultConfig();
        
        // 初始化管理器
        sideTextManager = new SideTextManager(this);
        playerPreferencesManager = new PlayerPreferencesManager(this);
        chatManager = new ChatManager(this);
        performanceTestManager = new PerformanceTestManager(this);
        
        // 注册监听器
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        
        // 初始化命令管理器
        new CommandManager(this);
        
        // 输出启用信息
        getLogger().info("MiSidetext 插件已成功启用！");
    }

    @Override
    public void onDisable() {
        // 清理资源
        if (sideTextManager != null) {
            sideTextManager.cleanup();
        }
        if (playerPreferencesManager != null) {
            playerPreferencesManager.cleanup();
        }
        if (chatManager != null) {
            chatManager.cleanup();
        }
        if (performanceTestManager != null) {
            // PerformanceTestManager会自动清理任务
        }
        
        getLogger().info("MiSidetext 插件已成功禁用！");
    }
    
    /**
     * 获取插件实例
     */
    public static MiSidetext getInstance() {
        return instance;
    }
    
    /**
     * 获取侧边文本管理器
     */
    public SideTextManager getSideTextManager() {
        return sideTextManager;
    }
    
    /**
     * 获取玩家偏好设置管理器
     */
    public PlayerPreferencesManager getPlayerPreferencesManager() {
        return playerPreferencesManager;
    }
    
    /**
     * 获取聊天管理器
     */
    public ChatManager getChatManager() {
        return chatManager;
    }
    
    /**
     * 获取性能测试管理器
     */
    public PerformanceTestManager getPerformanceTestManager() {
        return performanceTestManager;
    }
}
