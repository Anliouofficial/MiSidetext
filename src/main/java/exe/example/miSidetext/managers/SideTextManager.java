package exe.example.miSidetext.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

import exe.example.miSidetext.MiSidetext;

public class SideTextManager {

    private final MiSidetext plugin;
    private final Map<UUID, List<BukkitTask>> activeTasks = new ConcurrentHashMap<>();
    private final Map<UUID, List<TextDisplay>> activeDisplays = new ConcurrentHashMap<>();

    public SideTextManager(MiSidetext plugin) {
        this.plugin = plugin;
    }

    /**
     * 显示侧边文本效果（打字机效果版本）
     * @param player 玩家
     * @param message 消息内容
     */
    public void displaySideText(org.bukkit.entity.Player player, String message) {
        // 默认为1.0倍大小（普通聊天）
        displaySideTextWithTypewriter(player, message, 1.0);
    }
    
    /**
     * 显示侧边文本效果（打字机效果版本）
     * @param player 玩家
     * @param message 消息内容
     * @param textScale 文本缩放比例
     */
    public void displaySideText(org.bukkit.entity.Player player, String message, double textScale) {
        displaySideTextWithTypewriter(player, message, textScale);
    }
    
    private void displaySideTextWithTypewriter(org.bukkit.entity.Player player, String message, double textScale) {
        // 根据配置播放按钮触发声音
        if (plugin.getConfig().getBoolean("sound-effects.enabled", true)) {
            String soundType = plugin.getConfig().getString("sound-effects.click-sound", "block.stone_button.click_on");
            float volume = (float) plugin.getConfig().getDouble("sound-effects.volume", 1.0);
            float pitch = (float) plugin.getConfig().getDouble("sound-effects.pitch", 1.0);
            player.playSound(player.getLocation(), soundType, volume, pitch);
        }
        
        // 保留玩家之前的文本效果，允许多个字符实体同时存在
        List<BukkitTask> tasks = new ArrayList<>();
        List<TextDisplay> displays = new ArrayList<>();
        
        // 如果已有活跃显示，则添加到现有列表中
        synchronized (this) {
            if (activeDisplays.containsKey(player.getUniqueId())) {
                displays.addAll(activeDisplays.get(player.getUniqueId()));
            }
            if (activeTasks.containsKey(player.getUniqueId())) {
                tasks.addAll(activeTasks.get(player.getUniqueId()));
            }
        }

        // 使用玩家的个性化设置
        long typingSpeed = plugin.getPlayerPreferencesManager().getTypingSpeed(player);
        double fallDuration = plugin.getPlayerPreferencesManager().getFallDuration(player) * 20; // 转换为tick
        double bounceHeight = plugin.getPlayerPreferencesManager().getBounceHeight(player);
        double bouncePeriod = plugin.getConfig().getDouble("side-text.bounce-period") * 20;

        // 1. 方向计算：计算垂直于玩家视线的方向向量
        Location eyeLocation = player.getEyeLocation();
        Location baseLocation = calculateBasePosition(eyeLocation);
        
        // 2. 阅读方向：从左到右排列
        boolean leftToRight = true;
        
        // 3. 间距控制：根据字体缩放自动调整字符间距
        double charSpacing = calculateCharSpacing(textScale);
        
        // 4. 多行处理：将消息分割为多行
        List<String> lines = splitMessageIntoLines(message);
        int maxLineWidth = getMaxLineWidth(lines);
        double lineSpacing = 0.6 * textScale; // 行间距
        
        int totalDelay = 0;
        
        // 逐行处理文本
        // 存储每行的字符实体，用于换行时坠落
        final Map<Integer, List<TextDisplay>> lineDisplays = new HashMap<>();
        
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);
            
            // 5. 居中处理：计算每行文本的起始位置，使其居中显示
            double lineStartOffset = calculateCenteredOffset(line, maxLineWidth, charSpacing);
            
            // 所有行都使用相同的Y位置，实现覆盖显示效果
            double yOffset = 0.0; // 固定Y位置，让新行覆盖旧行
            
            // 为当前行创建显示列表
            lineDisplays.put(lineIndex, new ArrayList<>());
            
            // 逐字显示效果
            for (int i = 0; i < line.length(); i++) {
                final int charIndex = i;
                final char character = line.charAt(i);
                final double currentYOffset = yOffset;
                final double currentStartOffset = lineStartOffset;
                final int lineNumber = lineIndex;
                final String currentLine = line;
                
                // 计算字符显示延迟，包含行延迟
                long charDelay = (long) ((totalDelay + charIndex) * typingSpeed / 50);

                // 创建字符显示任务
                BukkitTask task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        // 计算字符位置 - 应用居中偏移
                        double xOffset = leftToRight ? 
                            currentStartOffset + charIndex * charSpacing : 
                            currentStartOffset - charIndex * charSpacing;
                            
                        // 如果是新行的第一个字符，将前一行相同位置的字符实体标记为准备坠落
                        if (charIndex == 0 && lineNumber > 0) {
                            synchronized (SideTextManager.this) {
                                // 找到前一行对应位置的字符实体
                                if (lineDisplays.containsKey(lineNumber - 1)) {
                                    List<TextDisplay> previousLine = lineDisplays.get(lineNumber - 1);
                                    for (TextDisplay prevDisplay : previousLine) {
                                        if (prevDisplay != null && !prevDisplay.isDead()) {
                                            // 立即应用坠落动画
                                            applyFallAnimation(prevDisplay, fallDuration);
                                            // 动画结束后移除实体
                                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                                if (prevDisplay != null && !prevDisplay.isDead()) {
                                                    prevDisplay.remove();
                                                    synchronized (SideTextManager.this) {
                                                        displays.remove(prevDisplay);
                                                    }
                                                }
                                            }, (long) fallDuration + 20); // 额外等待1秒确保动画完成
                                        }
                                    }
                                }
                            }
                        }
                            
                        Location charLocation = baseLocation.clone()
                            .add(xOffset, currentYOffset, 0);
                        
                        // 创建文本显示实体 - 确保参数正确
                        TextDisplay textDisplay = createTextDisplay(player.getWorld(), character, charLocation, textScale);
                        
                        if (textDisplay != null) {
                            // 添加到活跃列表和行显示映射
                            synchronized (SideTextManager.this) {
                                displays.add(textDisplay);
                                // 添加到当前行的显示列表
                                if (lineDisplays.containsKey(lineNumber)) {
                                    lineDisplays.get(lineNumber).add(textDisplay);
                                }
                            }

                            // 应用跳动动画
                            applyBounceAnimation(textDisplay, bounceHeight, bouncePeriod);
                            
                            // 为新字符添加轻微的出现效果，提升视觉体验
                            // 设置初始低亮度
                            textDisplay.setBrightness(new Display.Brightness((byte) 0, (byte) 0));
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (textDisplay != null && !textDisplay.isDead()) {
                                    // 恢复正常亮度
                                    textDisplay.setBrightness(new Display.Brightness((byte) 15, (byte) 15));
                                }
                            }, 1);
                        } else {
                            plugin.getLogger().warning("无法为玩家 " + player.getName() + " 创建文本实体，字符: '" + character + "'");
                        }
                    }
                }.runTaskLater(plugin, charDelay);

                tasks.add(task);
            }
            
            // 增加行之间的延迟
            totalDelay += line.length() + 10; // 每行之间额外延迟10个字符的时间（增加阅读时间）
            
            // 移除旧的行坠落处理，因为我们现在在新行第一个字符出现时处理坠落
        }
        
        // 为最后一行的字符设置坠落动画
        final int lastLineIndex = lines.size() - 1;
        long lastLineFallDelay = (long) (totalDelay * typingSpeed / 50);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<TextDisplay> lastLineChars = lineDisplays.get(lastLineIndex);
            if (lastLineChars != null) {
                for (TextDisplay display : lastLineChars) {
                    if (display != null && !display.isDead()) {
                        applyFallAnimation(display, fallDuration);
                        // 动画结束后移除实体
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (display != null && !display.isDead()) {
                                display.remove();
                                synchronized (SideTextManager.this) {
                                    displays.remove(display);
                                }
                            }
                        }, (long) fallDuration + 20); // 额外等待1秒确保动画完成
                    }
                }
            }
        }, lastLineFallDelay);

        // 存储活跃任务和显示实体
        synchronized (this) {
            activeTasks.put(player.getUniqueId(), tasks);
            activeDisplays.put(player.getUniqueId(), displays);
        }
    }
    
    /**
     * 计算基准位置，确保文本在玩家左前方可见
     */
    private Location calculateBasePosition(Location eyeLocation) {
        Location baseLocation = eyeLocation.clone();
        
        // 获取玩家视线方向
        org.bukkit.util.Vector direction = eyeLocation.getDirection().normalize();
        
        // 计算垂直于视线的方向向量（使用叉乘）
        org.bukkit.util.Vector up = new org.bukkit.util.Vector(0, 1, 0);
        org.bukkit.util.Vector sideVector = direction.crossProduct(up).normalize();
        
        // 将文本放在玩家侧面（左方向）和前方的混合位置
        // 左方向偏移：侧面向量乘以0.8个单位
        // 前方偏移：视线方向乘以0.5个单位
        baseLocation.add(sideVector.multiply(0.8)); // 左侧偏移
        baseLocation.add(direction.multiply(0.5));  // 前方偏移
        
        // 调整Y轴，确保文本在合适的高度
        baseLocation.setY(baseLocation.getY() + 0.2);
        
        return baseLocation;
    }
    
    /**
     * 根据文本缩放比例计算字符间距
     */
    private double calculateCharSpacing(double textScale) {
        // 基础间距为0.3，根据缩放比例调整
        return 0.3 * textScale;
    }
    
    /**
     * 将消息分割为多行
     */
    private List<String> splitMessageIntoLines(String message) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        int charCount = 0; // 计算所有字符数（包括标点）
        
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            
            // 添加字符到当前行
            currentLine.append(c);
            charCount++;
            
            // 当满7个字符或者遇到符号（标点）时换行
            // 注意：如果是最后一个字符，不进行换行处理，添加到最后一行
            if ((charCount >= 7 || isPunctuation(c)) && i < message.length() - 1) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
                charCount = 0;
            }
        }
        
        // 添加最后一行
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        
        // 如果没有分割（消息太短），确保至少有一行
        if (lines.isEmpty()) {
            lines.add(message);
        }
        
        return lines;
    }
    
    /**
     * 检查字符是否为标点符号
     */
    private boolean isPunctuation(char c) {
        return c == ',' || c == '.' || c == '!' || c == '?' || c == ';' || c == ':';
    }
    

    
    /**
     * 获取多行文本中的最大行宽度
     */
    private int getMaxLineWidth(List<String> lines) {
        int maxWidth = 0;
        for (String line : lines) {
            if (line.length() > maxWidth) {
                maxWidth = line.length();
            }
        }
        return maxWidth;
    }
    
    /**
     * 计算文本居中的起始偏移量
     */
    private double calculateCenteredOffset(String line, int maxLineWidth, double charSpacing) {
        // 计算居中偏移：(最大行宽度 - 当前行宽度) * 字符间距 / 2
        return (maxLineWidth - line.length()) * charSpacing / 2;
    }

    /**
     * 创建文本显示实体
     */
    private TextDisplay createTextDisplay(org.bukkit.World world, char character, Location location, double textScale) {
        // 确保位置有效
        if (world == null || location == null) {
            // 静默处理无效参数，不输出日志
            return null;
        }
        
        try {
            // 生成文本显示实体
            TextDisplay textDisplay = (TextDisplay) world.spawnEntity(location, EntityType.TEXT_DISPLAY);
            
            if (textDisplay == null) {
                // 静默处理创建失败，不输出日志
                return null;
            }
            
            // 设置显示属性 - 紫底白边效果，移除半透明黑色边框
            // 使用Minecraft颜色代码设置白色文本
            textDisplay.setText("§f" + character); // §f 是白色代码
            textDisplay.setBillboard(Display.Billboard.CENTER); // 始终面向玩家
            textDisplay.setBrightness(new Display.Brightness(15, 15)); // 最大亮度
            textDisplay.setInterpolationDuration(20); // 平滑过渡
            
            // 移除半透明黑色边框
            textDisplay.setShadowRadius(0.0f); // 禁用阴影半径
            textDisplay.setShadowStrength(0.0f); // 禁用阴影强度
            
            // 实现紫底白边效果
            textDisplay.setSeeThrough(false);
            textDisplay.setGlowing(true); // 启用发光效果
            textDisplay.setGlowColorOverride(Color.PURPLE); // 紫色背景效果
            
            // 调整比例使其更大更醒目，并应用文本缩放
            Transformation transformation = textDisplay.getTransformation();
            // 确保缩放功能正确应用，使单个字符的显示也支持缩放
            float scale = (float) (0.8f * textScale); // 根据缩放比例调整尺寸
            transformation.getScale().set(scale, scale, scale);
            textDisplay.setTransformation(transformation);
            
            return textDisplay;
        } catch (NullPointerException e) {
            // 仅处理空指针异常
            plugin.getLogger().warning("创建文本实体失败: 空指针异常");
            return null;
        } catch (RuntimeException e) {
            // 处理其他运行时异常
            plugin.getLogger().warning("创建文本实体失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 应用上下抖动动画 - 只保留Y方向的上下移动效果
     */
    private void applyBounceAnimation(TextDisplay textDisplay, double bounceHeight, double bouncePeriod) {
        // 为每个字符生成随机的基础相位偏移（final变量，不修改）
        final double basePhase = Math.random() * 2 * Math.PI;
        // 使用数组存储需要在运行时修改的参数
        final double[] phase1 = {Math.random() * 2 * Math.PI}; // 相位
        final double[] amplitudeFactor = {0.5 + Math.random() * 0.5}; // 振幅因子
        final double[] freqFactorY = {1.2 + Math.random() * 0.6}; // Y方向频率因子
        
        // 平滑因子，使变化更自然
        final double smoothFactor = 0.05;
        
        // 存储上一帧的Y位置，用于平滑过渡
        final double[] lastPosY = {0};
        
        new BukkitRunnable() {
            private int tick = 0;
            private double time = 0;

            @Override
            public void run() {
                if (textDisplay.isDead()) {
                    this.cancel();
                    return;
                }

                // 更新时间
                time += 0.1; // 调整时间步长以控制整体抖动速度
                
                // 使用多频率正弦波叠加生成更自然的上下抖动效果
                // 主要频率
                double mainFreq = 1.0 / (bouncePeriod * 0.1);
                
                // Y方向抖动 (上下移动)
                double yJitter = (Math.sin(time * mainFreq * freqFactorY[0] + basePhase) * 0.8 +
                                 Math.sin(time * mainFreq * 3.0 * freqFactorY[0] + phase1[0]) * 0.3) *
                                bounceHeight * amplitudeFactor[0];
                
                // 应用平滑过渡，避免抖动看起来卡顿
                double smoothedY = lastPosY[0] + (yJitter - lastPosY[0]) * smoothFactor;
                
                // 更新上一帧Y位置
                lastPosY[0] = smoothedY;
                
                // 应用Y方向位置变换
                Transformation transformation = textDisplay.getTransformation();
                transformation.getTranslation().set(0, (float) smoothedY, 0);
                textDisplay.setTransformation(transformation);
                
                tick++;
                
                // 每30刻重新计算随机参数，使上下抖动更加自然和随机
                if (tick % 30 == 0) {
                    // 重新生成Y方向的随机参数
                    phase1[0] = Math.random() * 2 * Math.PI;
                    freqFactorY[0] = 1.2 + Math.random() * 0.6;
                    amplitudeFactor[0] = 0.5 + Math.random() * 0.5;
                }
            }
        }.runTaskTimer(plugin, 0, 1); // 每个刻更新一次
    }

    /**
     * 应用物理坠落动画 - 使用直接物理模拟实现重力坠落效果，无需骑乘盔甲架
     */
    private void applyFallAnimation(TextDisplay textDisplay, double unusedFallDuration) {
        try {
            Location startLocation = textDisplay.getLocation();
            org.bukkit.World world = startLocation.getWorld();
            
            if (world == null) {
                return;
            }
            
            // 保存原始位置和大小
            Location currentLocation = startLocation.clone();
            
            // 保留原始大小
            Transformation transformation = textDisplay.getTransformation();
            float originalScale = transformation.getScale().x();
            
            // 设置物理参数
            final double[] velocityX = {(Math.random() - 0.5) * 0.2}; // 初始X速度
            final double[] velocityY = {0.15}; // 初始Y速度（轻微向上）
            final double[] velocityZ = {(Math.random() - 0.5) * 0.2}; // 初始Z速度
            final double gravity = -0.04; // 重力加速度
            final double friction = 0.98; // 摩擦力系数
            final double restitution = 0.6; // 弹性系数
            
            // 旋转参数
            final float[] currentXRot = {0};
            final float[] currentYRot = {0};
            final float[] currentZRot = {0};
            
            // 状态跟踪
            final boolean[] hasLanded = {false};
            final int[] noMovementTicks = {0};
            
            // 创建物理模拟任务
            final BukkitTask physicsTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (textDisplay.isDead() || hasLanded[0]) {
                        this.cancel();
                        return;
                    }
                    
                    // 应用重力
                    velocityY[0] += gravity;
                    
                    // 应用摩擦力
                    velocityX[0] *= friction;
                    velocityZ[0] *= friction;
                    
                    // 计算下一位置
                    double newX = currentLocation.getX() + velocityX[0];
                    double newY = currentLocation.getY() + velocityY[0];
                    double newZ = currentLocation.getZ() + velocityZ[0];
                    
                    // 碰撞检测 - X方向
                    Location blockLocX = new Location(world, Math.floor(newX), Math.floor(currentLocation.getY()), Math.floor(currentLocation.getZ()));
                    if (world.getBlockAt(blockLocX).getType().isSolid()) {
                        velocityX[0] = -velocityX[0] * restitution;
                        newX = currentLocation.getX();
                    }
                    
                    // 碰撞检测 - Z方向
                    Location blockLocZ = new Location(world, Math.floor(currentLocation.getX()), Math.floor(currentLocation.getY()), Math.floor(newZ));
                    if (world.getBlockAt(blockLocZ).getType().isSolid()) {
                        velocityZ[0] = -velocityZ[0] * restitution;
                        newZ = currentLocation.getZ();
                    }
                    
                    // 碰撞检测 - Y方向（地面检测）
                    Location blockLocY = new Location(world, Math.floor(newX), Math.floor(newY - 0.1), Math.floor(newZ));
                    if (world.getBlockAt(blockLocY).getType().isSolid()) {
                        // 着地或碰撞到方块顶部
                        if (velocityY[0] < 0) { // 只在下落时处理碰撞
                            velocityY[0] = -velocityY[0] * restitution * 0.5; // 减小反弹高度
                            newY = Math.floor(newY - 0.1) + 1.0; // 将位置放在方块顶部
                        }
                    }
                    
                    // 更新位置
                    currentLocation.setX(newX);
                    currentLocation.setY(newY);
                    currentLocation.setZ(newZ);
                    textDisplay.teleport(currentLocation);
                    
                    // 计算速度大小
                    double speed = Math.sqrt(
                        velocityX[0] * velocityX[0] +
                        velocityY[0] * velocityY[0] +
                        velocityZ[0] * velocityZ[0]
                    );
                    
                    // 检查是否静止不动
                    if (speed < 0.03) {
                        noMovementTicks[0]++;
                    } else {
                        noMovementTicks[0] = 0;
                    }
                    
                    // 检查是否着地（速度很小且静止了一段时间，或者位于地面上）
                    if ((noMovementTicks[0] >= 10) || 
                        (Math.abs(velocityY[0]) < 0.05 && 
                        world.getBlockAt(blockLocY).getType().isSolid())) {
                        
                        hasLanded[0] = true;
                        this.cancel();
                        
                        // 给字符一个轻微的随机倾斜，让它看起来像是自然地靠在方块上
                        Transformation finalTrans = textDisplay.getTransformation();
                        float finalXRot = (float)((Math.random() - 0.5) * 0.2); // 轻微X轴倾斜
                        float finalZRot = (float)((Math.random() - 0.5) * 0.2); // 轻微Z轴倾斜
                        finalTrans.getLeftRotation().set(finalXRot, 0, finalZRot, 1.0f);
                        textDisplay.setTransformation(finalTrans);
                        
                        // 自然延迟后渐隐，使效果更真实
                    final int randomDelay = 150 + (int)(Math.random() * 300); // 7.5-22.5秒延迟
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!textDisplay.isDead()) {
                            // 生成一些粒子效果，表示字符开始消失
                            spawnParticles(textDisplay.getLocation());
                            
                            // 应用渐变消失效果
                            fadeOutText(textDisplay, 80); // 4秒内渐隐，更平滑
                        }
                    }, randomDelay);
                    return;
                }
                    
                    // 只有在移动时才应用旋转
                    if (speed > 0.01) {
                        // 基于速度方向的自然旋转
                        currentYRot[0] += (float)(velocityX[0] * 0.2); // 左右移动产生Y轴旋转
                        currentXRot[0] += (float)(velocityZ[0] * 0.2); // 前后移动产生X轴旋转
                        currentZRot[0] += (float)((Math.random() - 0.5) * 0.02); // 轻微Z轴随机旋转
                    }
                    
                    // 应用物理旋转效果
                    Transformation t = textDisplay.getTransformation();
                    
                    // 计算旋转轴和角度
                    float rotX = (float)Math.sin(currentXRot[0] * 0.5) * 0.3f;
                    float rotY = (float)Math.sin(currentYRot[0] * 0.5) * 0.3f;
                    float rotZ = (float)Math.sin(currentZRot[0] * 0.5) * 0.9f;
                    float rotAngle = (float)Math.cos(currentXRot[0] * 0.5) + (float)Math.cos(currentYRot[0] * 0.5) + (float)Math.cos(currentZRot[0] * 0.5);
                    
                    // 归一化旋转轴
                    float length = (float)Math.sqrt(rotX*rotX + rotY*rotY + rotZ*rotZ + rotAngle*rotAngle);
                    if (length > 0) {
                        rotX /= length;
                        rotY /= length;
                        rotZ /= length;
                        rotAngle /= length;
                    }
                    
                    // 应用旋转
                    t.getLeftRotation().set(rotX, rotY, rotZ, rotAngle);
                    
                    // 保持原始大小不变
                    t.getScale().set(originalScale, originalScale, originalScale);
                    
                    textDisplay.setTransformation(t);
                }
            }.runTaskTimer(plugin, 0, 1); // 每个刻更新一次
            
            // 添加任务到活动任务列表，以便在需要时可以清理
            UUID entityId = textDisplay.getUniqueId();
            if (!activeTasks.containsKey(entityId)) {
                activeTasks.put(entityId, new ArrayList<>());
            }
            activeTasks.get(entityId).add(physicsTask);
            
            // 添加显示实体到活动显示列表
            if (!activeDisplays.containsKey(entityId)) {
                activeDisplays.put(entityId, new ArrayList<>());
            }
            activeDisplays.get(entityId).add(textDisplay);
            
        } catch (RuntimeException e) {
            // 静默处理运行时异常
        }
    }

    /**
     * 文本渐隐效果 - 通过修改文本颜色的透明度，实现平滑的消失效果
     */
    private void fadeOutText(TextDisplay textDisplay, int fadeTicks) {
        // 存储初始颜色
        final Color initialColor = textDisplay.getGlowColorOverride() != null ? 
            textDisplay.getGlowColorOverride() : Color.WHITE;
        
        new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                if (textDisplay.isDead()) {
                    this.cancel();
                    return;
                }

                // 计算透明度因子，从1.0到0.0线性变化
                float opacity = 1.0f - (float) tick / fadeTicks;
                
                if (opacity <= 0) {
                    opacity = 0;
                    textDisplay.remove();
                    this.cancel();
                    return;
                }
                
                // 通过修改发光颜色的透明度来实现渐隐效果
                // 使用ARGB值创建新的颜色，保持RGB值不变，只改变A值
                Color fadeColor = Color.fromRGB(
                    initialColor.getRed(),
                    initialColor.getGreen(),
                    initialColor.getBlue()
                );
                
                textDisplay.setGlowColorOverride(fadeColor);
                
                // 同时调整亮度以增强渐隐效果
                Display.Brightness brightness = new Display.Brightness(
                    (byte) (opacity * 15), 
                    (byte) (opacity * 15)
                );
                textDisplay.setBrightness(brightness);
                
                // 轻微缩小以增强消失感
                Transformation transformation = textDisplay.getTransformation();
                float scaleFactor = (float) (opacity * 0.8); // 假设原始比例是0.8
                transformation.getScale().set(scaleFactor, scaleFactor, scaleFactor);
                textDisplay.setTransformation(transformation);
                
                tick++;
            }
        }.runTaskTimer(plugin, 0, 1); // 每个刻更新一次
    }

    /**
     * 清理玩家的文本效果
     */
    public void cleanupPlayerEffects(UUID playerId) {
        // 取消所有任务
        if (activeTasks.containsKey(playerId)) {
            activeTasks.get(playerId).forEach(BukkitTask::cancel);
            activeTasks.remove(playerId);
        }
        
        // 移除所有显示实体
        if (activeDisplays.containsKey(playerId)) {
            activeDisplays.get(playerId).forEach(textDisplay -> {
                if (textDisplay != null && !textDisplay.isDead()) {
                    textDisplay.remove();
                }
            });
            activeDisplays.remove(playerId);
        }
    }

    /**
     * 生成粒子效果
     */
    private void spawnParticles(Location location) {
        try {
            String particleType = plugin.getConfig().getString("particle-effects.particle-type", "ENCHANTMENT_TABLE");
            int count = plugin.getConfig().getInt("particle-effects.particle-count", 3);
            
            // 尝试获取粒子枚举
            org.bukkit.Particle particle = org.bukkit.Particle.valueOf(particleType);
            location.getWorld().spawnParticle(particle, location, count, 0.1, 0.1, 0.1, 0.01);
        } catch (Exception e) {
            // 如果粒子类型无效，使用默认粒子
            location.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, location, 3, 0.1, 0.1, 0.1, 0.01);
        }
    }
    
    /**
     * 清理所有资源
     */
    public void cleanup() {
        // 取消所有任务
        activeTasks.values().forEach(tasks -> tasks.forEach(BukkitTask::cancel));
        activeTasks.clear();
        
        // 移除所有显示实体
        activeDisplays.values().forEach(displays -> displays.forEach(textDisplay -> {
            if (textDisplay != null && !textDisplay.isDead()) {
                textDisplay.remove();
            }
        }));
        activeDisplays.clear();
    }
}