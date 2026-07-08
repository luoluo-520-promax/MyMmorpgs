$ErrorActionPreference = "Stop"
$root = "c:\Users\ASUS\IdeaProjects\test\MyMmorpg"
$commonJava = Join-Path $root "mmorpg-common\src\main\java\cn\itcast\demo\mymmorpg"
$commonGroovy = Join-Path $root "mmorpg-common\src\main\groovy\cn\itcast\demo\mymmorpg"
$playerJava = Join-Path $root "player-service\src\main\java\cn\itcast\demo\mymmorpg"
$playerGroovy = Join-Path $root "player-service\src\main\groovy\cn\itcast\demo\mymmorpg"
$battleJava = Join-Path $root "battle-service\src\main\java\cn\itcast\demo\mymmorpg"
$battleGroovy = Join-Path $root "battle-service\src\main\groovy\cn\itcast\demo\mymmorpg"
$activityJava = Join-Path $root "activity-service\src\main\java\cn\itcast\demo\mymmorpg"
$activityGroovy = Join-Path $root "activity-service\src\main\groovy\cn\itcast\demo\mymmorpg"

function Ensure-Dir($path) {
    if (-not (Test-Path $path)) { New-Item -ItemType Directory -Path $path -Force | Out-Null }
}

function Move-Package($srcDir, $destDir) {
    if (Test-Path $srcDir) {
        Ensure-Dir (Split-Path $destDir -Parent)
        if (Test-Path $destDir) { Remove-Item $destDir -Recurse -Force }
        Move-Item $srcDir $destDir
        Write-Host "Moved package: $srcDir -> $destDir"
    }
}

function Move-File($src, $destDir) {
    if (Test-Path $src) {
        Ensure-Dir $destDir
        $dest = Join-Path $destDir (Split-Path $src -Leaf)
        if (Test-Path $dest) { Remove-Item $dest -Force }
        Move-Item $src $dest
        Write-Host "Moved file: $(Split-Path $src -Leaf)"
    }
}

function Remove-FileIfExists($path) {
    if (Test-Path $path) {
        Remove-Item $path -Force
        Write-Host "Deleted duplicate: $(Split-Path $path -Leaf)"
    }
}

# --- 1. Delete duplicates already present in battle/activity modules ---
$duplicates = @(
    "$commonJava\config\BattleJpaConfiguration.java",
    "$commonJava\model\BattleSceneFactory.java",
    "$commonJava\rpc\FightRpcServer.java",
    "$commonJava\rpc\FightRpcServerHandler.java",
    "$commonJava\service\BattleEventPublisher.java",
    "$commonJava\service\BattleService.java",
    "$commonJava\service\NoOpBattleEventPublisher.java",
    "$commonJava\service\RocketMqBattleEventPublisher.java",
    "$commonJava\support\BattleServiceJmx.java",
    "$commonJava\web\InternalBattleController.java",
    "$commonJava\service\ActivityService.java",
    "$commonJava\service\NoOpActivityEventPublisher.java",
    "$commonJava\service\RocketMqActivityEventPublisher.java",
    "$commonJava\support\ActivityServiceJmx.java",
    "$commonJava\web\InternalActivityController.java"
)
foreach ($f in $duplicates) { Remove-FileIfExists $f }

# --- 2. Move entire player packages ---
Move-Package "$commonJava\handler" "$playerJava\handler"
Move-Package "$commonJava\net" "$playerJava\net"
Move-Package "$commonJava\event" "$playerJava\event"
Move-Package "$commonJava\rpc" "$playerJava\rpc"

# --- 3. Move player config files ---
$playerConfigs = @(
    "CacheConfig.java", "DevDataLoader.java", "DynamicCacheResolver.java",
    "FunctionBoxStore.java", "FunctionConfigProperties.java", "FunctionConfiguration.java",
    "PasswordConfig.java", "PolicyConfiguration.java", "SocketServerAutoConfiguration.java",
    "WebSocketConfig.java"
)
foreach ($c in $playerConfigs) {
    Move-File "$commonJava\config\$c" "$playerJava\config"
}

# --- 4. Move admin entities ---
Get-ChildItem "$commonJava\entity\Admin*.java" -ErrorAction SilentlyContinue | ForEach-Object {
    Move-File $_.FullName "$playerJava\entity"
}

# --- 5. Move player model (function system) ---
$playerModels = @("FunctionOpenType.java", "FunctionBox.java", "ConfigFunction.java", "FunctionId.java")
foreach ($m in $playerModels) {
    Move-File "$commonJava\model\$m" "$playerJava\model"
}

# --- 6. Move player services ---
$playerServices = @(
    "AccountCredentialManager.java", "AccountCredentialOutcome.java", "AccountPlayerService.java",
    "AdminComplaintService.java", "AdminPermissionService.java", "AuthTokenService.java",
    "BagService.java", "BattleCommandGateway.java", "BattleFacade.java",
    "ChatAsyncDbService.java", "ChatService.java", "ConfigQueryService.java",
    "FunctionConfigService.java", "FunctionFacade.java", "FunctionService.java",
    "GameDataWarmupService.java", "GameDataWarmupStarter.java", "LoginAdmissionManager.java",
    "PlayerDataAsyncPreloadService.java", "PlayerDataLoadStateService.java",
    "PlayerEntityCacheService.java", "PlayerLogoutAccessManager.java", "PlayerProgressService.java",
    "PlayerPushRegistry.java", "PlayerSelectionAccessManager.java", "PlayerSessionService.java",
    "PlayerTimerPersistenceService.java", "SceneActorService.java", "SkillService.java",
    "ActivityCommandGateway.java", "ActivityFacade.java", "BuffFacade.java"
)
foreach ($s in $playerServices) {
    Move-File "$commonJava\service\$s" "$playerJava\service"
}

# --- 7. Move player support ---
$playerSupport = @(
    "BagServiceJmx.java", "ChatServiceJmx.java", "ConfigManager.java", "ConfigManagerBinder.java",
    "ChatPolicy.java", "ItemPolicy.java", "LoginPolicy.java", "ScenePolicy.java",
    "SkillPolicy.java", "PlayerServiceJmx.java", "SceneServiceJmx.java", "SkillServiceJmx.java"
)
foreach ($s in $playerSupport) {
    Move-File "$commonJava\support\$s" "$playerJava\support"
}

# --- 8. Move player web ---
Move-File "$commonJava\web\SecurityController.java" "$playerJava\web"

# --- 9. Move player groovy policies ---
$playerGroovies = @(
    "GroovyScenePolicy.groovy", "GroovyLoginPolicy.groovy", "GroovySkillPolicy.groovy",
    "GroovyChatPolicy.groovy", "GroovyItemPolicy.groovy"
)
foreach ($g in $playerGroovies) {
    Move-File "$commonGroovy\support\$g" "$playerGroovy\support"
}

# --- 10. Move battle domain ---
$battleServices = @(
    "BuffService.java", "BuffEventPublisher.java", "NoOpBuffEventPublisher.java", "RocketMqBuffEventPublisher.java"
)
foreach ($s in $battleServices) {
    Move-File "$commonJava\service\$s" "$battleJava\service"
}
$battleSupport = @("BattlePolicy.java", "BuffPolicy.java", "BuffServiceJmx.java")
foreach ($s in $battleSupport) {
    Move-File "$commonJava\support\$s" "$battleJava\support"
}
$battleModels = @(
    "MonsterWaveSimpleFactory.java", "PeriodicHotBuff.java", "PeriodicDotBuff.java",
    "PeriodicBuffRegistry.java", "PeriodicBuffRuntime.java", "PeriodicBuffFactory.java",
    "PeriodicBuff.java", "GenericPeriodicBuff.java"
)
foreach ($m in $battleModels) {
    Move-File "$commonJava\model\$m" "$battleJava\model"
}
Ensure-Dir "$battleGroovy\support"
Move-File "$commonGroovy\support\GroovyBattlePolicy.groovy" "$battleGroovy\support"
Move-File "$commonGroovy\support\GroovyBuffPolicy.groovy" "$battleGroovy\support"

# --- 11. Move activity domain ---
Move-File "$commonJava\entity\Activity.java" "$activityJava\entity"
Move-File "$commonJava\repository\ActivityRepository.java" "$activityJava\repository"
$activityServices = @("ActivityPlayerProgressStore.java", "ActivityTypes.java")
foreach ($s in $activityServices) {
    Move-File "$commonJava\service\$s" "$activityJava\service"
}
$activityModels = @("ActivityConfigPayload.java", "ActivityTypes.java", "PlayerActivityProgress.java", "RewardTierPayload.java")
foreach ($m in $activityModels) {
    Move-File "$commonJava\model\$m" "$activityJava\model"
}
Move-File "$commonJava\support\ActivityPolicy.java" "$activityJava\support"
Move-File "$commonGroovy\support\GroovyActivityPolicy.groovy" "$activityGroovy\support"

Write-Host "Migration script completed."
