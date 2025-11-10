package exe.example.miSidetext.managers;

import exe.example.miSidetext.MiSidetext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PerformanceTestManager {
    private final MiSidetext plugin;
    private final SideTextManager sideTextManager;
    private final ConcurrentHashMap<UUID, PerformanceTest> activeTests = new ConcurrentHashMap<>();
    
    // TPS监控相关
    private long[] ticks = new long[600]; // 存储最近10分钟的tick时间
    private int lastIndex = 0;
    private long lastTime = System.nanoTime();
    
    public PerformanceTestManager(MiSidetext plugin) {
        this.plugin = plugin;
        this.sideTextManager = plugin.getSideTextManager();
        
        // 启动TPS监控任务
        startTPSMonitor();
    }
    
    /**
     * 启动TPS监控任务
     */
    private void startTPSMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.nanoTime();
                long elapsed = currentTime - lastTime;
                lastTime = currentTime;
                
                // 更新tick记录
                synchronized (ticks) {
                    ticks[lastIndex] = elapsed;
                    lastIndex = (lastIndex + 1) % ticks.length;
                }
            }
        }.runTaskTimer(plugin, 1, 1); // 每个tick执行一次
    }
    
    /**
     * 获取当前TPS
     */
    public double getTPS() {
        return getTPS(1); // 默认返回最近1秒的TPS
    }
    
    /**
     * 获取指定时间范围内的TPS
     * @param seconds 时间范围（秒）
     */
    public double getTPS(int seconds) {
        synchronized (ticks) {
            int count = Math.min(seconds * 20, ticks.length);
            long totalTime = 0;
            
            for (int i = 0; i < count; i++) {
                int index = (lastIndex - i - 1 + ticks.length) % ticks.length;
                totalTime += ticks[index];
            }
            
            if (count == 0 || totalTime == 0) return 20.0;
            
            double expectedTime = count * 50000000.0; // 每个tick应该是50ms(50,000,000ns)
            return Math.min(20.0, expectedTime / totalTime * 20.0);
        }
    }
    
    /**
     * 开始性能测试
     * @param player 执行测试的玩家
     * @param duration 测试持续时间（秒）
     * @param frequency 生成频率（每x个tick生成一次）
     * @param textLength 每次生成的文本长度
     */
    public void startTest(Player player, int duration, int frequency, int textLength) {
        UUID playerId = player.getUniqueId();
        
        // 停止之前可能存在的测试
        stopTest(player);
        
        PerformanceTest test = new PerformanceTest(player, duration, frequency, textLength);
        activeTests.put(playerId, test);
        
        player.sendMessage("§6[性能测试] §f开始测试 - 持续时间: " + duration + "秒, 生成频率: 每" + frequency + "tick, 文本长度: " + textLength);
        player.sendMessage("§6[性能测试] §f当前TPS: " + String.format("%.2f", getTPS()));
        
        test.start();
    }
    
    /**
     * 停止性能测试
     */
    public void stopTest(Player player) {
        UUID playerId = player.getUniqueId();
        PerformanceTest test = activeTests.remove(playerId);
        
        if (test != null) {
            test.stop();
            player.sendMessage("§6[性能测试] §f测试已停止");
        } else {
            player.sendMessage("§6[性能测试] §f您没有正在进行的测试");
        }
    }
    
    /**
     * 获取测试状态
     */
    public void getTestStatus(Player player) {
        UUID playerId = player.getUniqueId();
        PerformanceTest test = activeTests.get(playerId);
        
        if (test != null) {
            test.sendStatus();
        } else {
            player.sendMessage("§6[性能测试] §f当前TPS: " + String.format("%.2f", getTPS()));
            player.sendMessage("§6[性能测试] §f1分钟TPS: " + String.format("%.2f", getTPS(60)));
            player.sendMessage("§6[性能测试] §f没有正在进行的测试");
        }
    }
    
    /**
     * 内部类：性能测试实例
     */
    private class PerformanceTest {
        private final Player player;
        private final int duration;
        private final int frequency;
        private final int textLength;
        private final long startTime;
        private BukkitTask testTask;
        private int entitiesSpawned = 0;
        private List<Double> tpsHistory = new ArrayList<>();
        
        public PerformanceTest(Player player, int duration, int frequency, int textLength) {
            this.player = player;
            this.duration = duration;
            this.frequency = frequency;
            this.textLength = textLength;
            this.startTime = System.currentTimeMillis();
        }
        
        public void start() {
            testTask = new BukkitRunnable() {
                @Override
                public void run() {
                    // 检查测试是否应该结束
                    if (System.currentTimeMillis() - startTime > duration * 1000) {
                        completeTest();
                        return;
                    }
                    
                    // 生成随机文本并显示
                    String randomText = generateRandomText(textLength);
                    sideTextManager.displaySideText(player, randomText);
                    entitiesSpawned += randomText.length();
                    
                    // 记录TPS
                    tpsHistory.add(getTPS());
                }
            }.runTaskTimer(plugin, 0, frequency);
        }
        
        public void stop() {
            if (testTask != null) {
                testTask.cancel();
                testTask = null;
            }
        }
        
        private void completeTest() {
            stop();
            activeTests.remove(player.getUniqueId());
            
            // 计算平均TPS和最低TPS
            double avgTPS = tpsHistory.stream().mapToDouble(Double::doubleValue).average().orElse(20.0);
            double minTPS = tpsHistory.stream().mapToDouble(Double::doubleValue).min().orElse(20.0);
            
            player.sendMessage("§6[性能测试] §f测试完成！");
            player.sendMessage("§6[性能测试] §f生成实体总数: " + entitiesSpawned);
            player.sendMessage("§6[性能测试] §f平均TPS: " + String.format("%.2f", avgTPS));
            player.sendMessage("§6[性能测试] §f最低TPS: " + String.format("%.2f", minTPS));
            player.sendMessage("§6[性能测试] §f当前TPS: " + String.format("%.2f", getTPS()));
        }
        
        public void sendStatus() {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            double currentTPS = getTPS();
            
            player.sendMessage("§6[性能测试] §f测试进行中...");
            player.sendMessage("§6[性能测试] §f已运行: " + elapsed + "/" + duration + "秒");
            player.sendMessage("§6[性能测试] §f已生成实体: " + entitiesSpawned);
            player.sendMessage("§6[性能测试] §f当前TPS: " + String.format("%.2f", currentTPS));
        }
        
        private String generateRandomText(int length) {
            StringBuilder sb = new StringBuilder(length);
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            
            for (int i = 0; i < length; i++) {
                sb.append(chars.charAt((int) (Math.random() * chars.length())));
            }
            
            return sb.toString();
        }
    }
}