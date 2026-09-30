package cn.itcast.demo.mymmorpg.world.gameplay;

import cn.itcast.demo.mymmorpg.aoi.VisualSignificanceScheduler;
import cn.itcast.demo.mymmorpg.world.ai.CompanionGhostService;
import cn.itcast.demo.mymmorpg.world.ai.EnvironmentUtilizationAI;
import cn.itcast.demo.mymmorpg.world.ai.SquadCommanderService;
import cn.itcast.demo.mymmorpg.world.battle.BattleReplayService;
import cn.itcast.demo.mymmorpg.world.battle.BattleTriggerService;
import cn.itcast.demo.mymmorpg.world.battle.BulletTimeService;
import cn.itcast.demo.mymmorpg.sync.ServerShadowService;
import cn.itcast.demo.mymmorpg.world.battle.ClientPredictedActionService;
import cn.itcast.demo.mymmorpg.world.battle.CombatAssistService;
import cn.itcast.demo.mymmorpg.world.battle.HitFeedbackService;
import cn.itcast.demo.mymmorpg.world.battle.DualClockService;
import cn.itcast.demo.mymmorpg.world.battle.CombatEventRingBuffer;
import cn.itcast.demo.mymmorpg.world.scene.BattleScenePodAllocator;
import cn.itcast.demo.mymmorpg.world.ecosystem.EcoGraphService;
import cn.itcast.demo.mymmorpg.world.ecosystem.EcoMigrationScheduler;
import cn.itcast.demo.mymmorpg.world.endgame.AffixShuffleService;
import cn.itcast.demo.mymmorpg.world.explore.ExplorationVitalityService;
import cn.itcast.demo.mymmorpg.world.explore.OculiResonanceService;
import cn.itcast.demo.mymmorpg.world.puzzle.DeterministicMutationService;
import cn.itcast.demo.mymmorpg.world.social.CoopCampService;
import cn.itcast.demo.mymmorpg.world.social.FightContributionPoolService;
import cn.itcast.demo.mymmorpg.world.social.PhantomBorrowService;
import cn.itcast.demo.mymmorpg.world.social.SocialTokenService;
import cn.itcast.demo.mymmorpg.world.traverse.TraverseMomentumService;
import cn.itcast.demo.mymmorpg.world.battle.InputBufferService;
import cn.itcast.demo.mymmorpg.world.battle.PoiseService;
import cn.itcast.demo.mymmorpg.world.battle.PrePlaybackService;
import cn.itcast.demo.mymmorpg.world.battle.ProjectileCurveService;
import cn.itcast.demo.mymmorpg.world.battle.ReactionValidator;
import cn.itcast.demo.mymmorpg.world.content.OpenWorldConfigPatchService;
import cn.itcast.demo.mymmorpg.world.content.ProceduralPlacementService;
import cn.itcast.demo.mymmorpg.world.content.RareEliteSpawnService;
import cn.itcast.demo.mymmorpg.world.economy.AuctionHouseService;
import cn.itcast.demo.mymmorpg.world.economy.CraftingTreeService;
import cn.itcast.demo.mymmorpg.world.ecosystem.AffinityService;
import cn.itcast.demo.mymmorpg.world.ecosystem.EcoNarrativeBridgeService;
import cn.itcast.demo.mymmorpg.world.ecosystem.EcologicalTableauService;
import cn.itcast.demo.mymmorpg.world.ecosystem.EcosystemBehaviorService;
import cn.itcast.demo.mymmorpg.world.explore.RegionImpactService;
import cn.itcast.demo.mymmorpg.world.ecosystem.EnvironmentalStoryService;
import cn.itcast.demo.mymmorpg.world.ecosystem.WorldExplorationFeedbackService;
import cn.itcast.demo.mymmorpg.world.endgame.AsymmetricPlayService;
import cn.itcast.demo.mymmorpg.world.endgame.PropTransformService;
import cn.itcast.demo.mymmorpg.world.endgame.RogueFateCardService;
import cn.itcast.demo.mymmorpg.world.endgame.SiegeWarService;
import cn.itcast.demo.mymmorpg.world.explore.CollectibleService;
import cn.itcast.demo.mymmorpg.world.explore.ExplorationCompassService;
import cn.itcast.demo.mymmorpg.world.explore.ExplorationContentKind;
import cn.itcast.demo.mymmorpg.world.explore.ExplorationWorldImpactService;
import cn.itcast.demo.mymmorpg.world.explore.MapMarkerService;
import cn.itcast.demo.mymmorpg.world.explore.ExplorationPoint;
import cn.itcast.demo.mymmorpg.world.explore.ExplorationRewardLoopService;
import cn.itcast.demo.mymmorpg.world.explore.GuidanceChainService;
import cn.itcast.demo.mymmorpg.world.explore.LandmarkWonder;
import cn.itcast.demo.mymmorpg.world.explore.LandmarkWonderService;
import cn.itcast.demo.mymmorpg.world.explore.RegionAwakeningService;
import cn.itcast.demo.mymmorpg.world.explore.PerceptionModifierService;
import cn.itcast.demo.mymmorpg.world.explore.RegionHeatService;
import cn.itcast.demo.mymmorpg.world.explore.RegionProgressService;
import cn.itcast.demo.mymmorpg.world.explore.WorldCoreDungeonService;
import cn.itcast.demo.mymmorpg.world.explore.WorldExplorationBalancer;
import cn.itcast.demo.mymmorpg.world.level.AscensionDungeonService;
import cn.itcast.demo.mymmorpg.world.narrative.CoopNarrativeProxy;
import cn.itcast.demo.mymmorpg.world.narrative.DynamicCutsceneTrigger;
import cn.itcast.demo.mymmorpg.world.narrative.ExplorationSkillService;
import cn.itcast.demo.mymmorpg.world.narrative.InstanceRegionLockService;
import cn.itcast.demo.mymmorpg.world.narrative.PlayerChronicleService;
import cn.itcast.demo.mymmorpg.world.narrative.RegionTugOfWarService;
import cn.itcast.demo.mymmorpg.world.narrative.ServerEpochService;
import cn.itcast.demo.mymmorpg.world.narrative.StoryBranchService;
import cn.itcast.demo.mymmorpg.world.narrative.StoryStateMachine;
import cn.itcast.demo.mymmorpg.world.narrative.WorldEncounterService;
import cn.itcast.demo.mymmorpg.world.progression.BuildRecommendationService;
import cn.itcast.demo.mymmorpg.world.progression.ConstellationService;
import cn.itcast.demo.mymmorpg.world.progression.FlexibleDailyQuestService;
import cn.itcast.demo.mymmorpg.world.progression.ResourceAutomationService;
import cn.itcast.demo.mymmorpg.world.progression.SkillCastValidator;
import cn.itcast.demo.mymmorpg.world.puzzle.CoopPuzzleService;
import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsAuthorityService;
import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsLayerService;
import cn.itcast.demo.mymmorpg.world.puzzle.PuzzleTemplateService;
import cn.itcast.demo.mymmorpg.world.puzzle.RuleTriggerService;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainMutationService;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainStateVector;
import cn.itcast.demo.mymmorpg.world.puzzle.TerrainTopologyGraph;
import cn.itcast.demo.mymmorpg.world.ownership.WorldOwnershipContext;
import cn.itcast.demo.mymmorpg.world.coop.CoopRoomElectionService;
import cn.itcast.demo.mymmorpg.world.coop.CoopTerrainSnapshotService;
import cn.itcast.demo.mymmorpg.world.civilization.CivilizationScheduleService;
import cn.itcast.demo.mymmorpg.world.feel.PhysicalDetailService;
import cn.itcast.demo.mymmorpg.world.feel.TerrainDetailTracker;
import cn.itcast.demo.mymmorpg.world.physics.GrabThrowService;
import cn.itcast.demo.mymmorpg.world.physics.MassMomentumService;
import cn.itcast.demo.mymmorpg.world.time.GameTimeKeeper;
import cn.itcast.demo.mymmorpg.world.puzzle.WorldMutabilityService;
import cn.itcast.demo.mymmorpg.world.puzzle.ZoneLifecycleManager;
import cn.itcast.demo.mymmorpg.world.sideplay.CreatureCatchService;
import cn.itcast.demo.mymmorpg.world.sideplay.CreatureUtilityService;
import cn.itcast.demo.mymmorpg.world.sideplay.ExtractionMissionService;
import cn.itcast.demo.mymmorpg.world.sideplay.HandbookService;
import cn.itcast.demo.mymmorpg.world.sideplay.HomelandGuardService;
import cn.itcast.demo.mymmorpg.world.sideplay.HomelandService;
import cn.itcast.demo.mymmorpg.world.sideplay.LeisureActivityService;
import cn.itcast.demo.mymmorpg.world.sideplay.OpenWorldHomesteadService;
import cn.itcast.demo.mymmorpg.world.sideplay.WorldCookingService;
import cn.itcast.demo.mymmorpg.world.social.PublicMarkService;
import cn.itcast.demo.mymmorpg.world.social.RegionWorldChannelService;
import cn.itcast.demo.mymmorpg.world.team.TeamCompositionService;
import cn.itcast.demo.mymmorpg.world.tick.GameplayTickSlicer;
import cn.itcast.demo.mymmorpg.world.traverse.ClimbAttackService;
import cn.itcast.demo.mymmorpg.world.traverse.ClimbRestPointService;
import cn.itcast.demo.mymmorpg.world.traverse.RegionalTraverseService;
import cn.itcast.demo.mymmorpg.world.traverse.UniversalTraversalService;
import cn.itcast.demo.mymmorpg.world.traverse.EnvironmentInteractionService;
import cn.itcast.demo.mymmorpg.world.traverse.FallAttackValidator;
import cn.itcast.demo.mymmorpg.world.traverse.GrappleNodeService;
import cn.itcast.demo.mymmorpg.world.traverse.GrapplePhysicsService;
import cn.itcast.demo.mymmorpg.world.traverse.InputConfidenceAnalyzer;
import cn.itcast.demo.mymmorpg.world.traverse.MoveTrajectoryValidator;
import cn.itcast.demo.mymmorpg.world.traverse.MovementAdmissionService;
import cn.itcast.demo.mymmorpg.world.traverse.StaminaConsumeService;
import cn.itcast.demo.mymmorpg.world.traverse.TraverseModeService;
import cn.itcast.demo.mymmorpg.world.traverse.UnderwaterPhysicsService;
import cn.itcast.demo.mymmorpg.world.traverse.VehicleCombatService;
import cn.itcast.demo.mymmorpg.world.traverse.VehicleSyncService;
import cn.itcast.demo.mymmorpg.world.traverse.WorldSkillService;
import cn.itcast.demo.mymmorpg.world.traverse.WorldSurpriseService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 二游大世界「产品玩法」门面：探索循环 / 解谜引擎 / 叙事偶遇 / 自由移动交互 / 副玩法 / 社交痕迹 / P9–P11。
 */
@Service
public class OpenWorldGameplayFacade {

    private final ExplorationRewardLoopService exploration = new ExplorationRewardLoopService();
    private final LandmarkWonderService landmarks = new LandmarkWonderService();
    private final RegionImpactService regions = new RegionImpactService();
    private final RegionProgressService regionProgress = new RegionProgressService();
    private final CollectibleService collectibles = new CollectibleService();
    private final GuidanceChainService guidanceChains = new GuidanceChainService();
    private final StoryBranchService story = new StoryBranchService();
    private final WorldEncounterService encounters = new WorldEncounterService();
    private final ExplorationSkillService exploreSkills = new ExplorationSkillService();
    private final TraverseModeService traverse = new TraverseModeService();
    private final StaminaConsumeService stamina = new StaminaConsumeService();
    private final GrappleNodeService grappleNodes = new GrappleNodeService();
    private final UnderwaterPhysicsService underwater = new UnderwaterPhysicsService();
    private final MovementAdmissionService movementAdmission =
            new MovementAdmissionService(traverse, stamina, grappleNodes, underwater);
    private final VehicleSyncService vehicles = new VehicleSyncService();
    private final WorldSkillService worldSkills = new WorldSkillService();
    private final EnvironmentInteractionService environment = new EnvironmentInteractionService();
    private final WorldSurpriseService surprises = new WorldSurpriseService();
    private final OpenWorldHomesteadService homestead = new OpenWorldHomesteadService();
    private final HomelandService homeland = new HomelandService();
    private final HomelandGuardService homelandGuard = new HomelandGuardService(homeland);
    private final CreatureCatchService creatures = new CreatureCatchService();
    private final CreatureUtilityService creatureUtility = new CreatureUtilityService(creatures);
    private final LeisureActivityService leisure = new LeisureActivityService();
    private final ExtractionMissionService extraction = new ExtractionMissionService();
    private final WorldCookingService cooking = new WorldCookingService();
    private final RuleTriggerService rules = new RuleTriggerService();
    private final CoopPuzzleService coopPuzzles = new CoopPuzzleService();
    private final WorldMutabilityService mutability = new WorldMutabilityService();
    private final FallAttackValidator fallAttack = new FallAttackValidator(mutability);
    private final ZoneLifecycleManager zoneLifecycle = new ZoneLifecycleManager();
    private final PhysicsLayerService physics = new PhysicsLayerService(zoneLifecycle);
    private final PhysicsAuthorityService physicsAuthority = new PhysicsAuthorityService();
    private final TerrainTopologyGraph terrainTopology = new TerrainTopologyGraph();
    private final PuzzleTemplateService puzzles = new PuzzleTemplateService();
    private final PublicMarkService publicMarks = new PublicMarkService();
    private final RegionWorldChannelService regionChannel = new RegionWorldChannelService();
    private final ProceduralPlacementService placement = new ProceduralPlacementService();
    private final RareEliteSpawnService rareElites = new RareEliteSpawnService(placement);
    private final OpenWorldConfigPatchService configPatch = new OpenWorldConfigPatchService();
    private final BulletTimeService bulletTime = new BulletTimeService();
    private final ReactionValidator reactions = new ReactionValidator(bulletTime);
    private final InputBufferService inputBuffer = new InputBufferService(reactions);
    private final PoiseService poise = new PoiseService();
    private final BattleTriggerService battleTriggers = new BattleTriggerService();
    private final BattleReplayService replays = new BattleReplayService();
    private final PlayerChronicleService chronicle = new PlayerChronicleService();
    private final AscensionDungeonService ascension = new AscensionDungeonService();
    private final ConstellationService constellation = new ConstellationService();
    private final SkillCastValidator skillCast = new SkillCastValidator(constellation);
    private final RegionAwakeningService regionAwakening = new RegionAwakeningService(regionProgress);
    private final WorldCoreDungeonService worldCore = new WorldCoreDungeonService(landmarks);

    private final EcosystemBehaviorService ecosystem = new EcosystemBehaviorService();
    private final EcoGraphService ecoGraph = new EcoGraphService();
    private final AffinityService affinity = new AffinityService(collectibles);
    private final GrapplePhysicsService grapplePhysics = new GrapplePhysicsService();
    private final VehicleCombatService vehicleCombat = new VehicleCombatService();
    private final ClimbAttackService climbAttack = new ClimbAttackService();
    private final TerrainMutationService terrainMutation = new TerrainMutationService();
    private final ProjectileCurveService projectileCurve = new ProjectileCurveService();
    private final ServerEpochService serverEpoch = new ServerEpochService();
    private final RegionTugOfWarService tugOfWar = new RegionTugOfWarService(regions);
    private final DynamicCutsceneTrigger cutsceneTrigger = new DynamicCutsceneTrigger();
    private final SiegeWarService siegeWar = new SiegeWarService();
    private final PropTransformService propTransform = new PropTransformService();
    private final AsymmetricPlayService asymmetricPlay = new AsymmetricPlayService(propTransform);
    private final CraftingTreeService crafting = new CraftingTreeService();
    private final AuctionHouseService auctionHouse = new AuctionHouseService();
    private final HitFeedbackService hitFeedback = new HitFeedbackService();
    private final DualClockService dualClock = new DualClockService();
    private final CombatEventRingBuffer combatEvents = new CombatEventRingBuffer();
    private final BattleScenePodAllocator battleScenePods = new BattleScenePodAllocator();
    private final PrePlaybackService prePlayback = new PrePlaybackService();
    private final SquadCommanderService squadCommander = new SquadCommanderService();
    private final EnvironmentUtilizationAI envUtilAi = new EnvironmentUtilizationAI();

    private final TeamCompositionService teamComposition = new TeamCompositionService();
    private final InstanceRegionLockService instanceRegionLock = new InstanceRegionLockService(regions);
    private final StoryStateMachine storyStateMachine = new StoryStateMachine(chronicle);
    private final CoopNarrativeProxy coopNarrative = new CoopNarrativeProxy(chronicle, regions);
    private final HandbookService handbook = new HandbookService(regions);
    private final RogueFateCardService rogueFate = new RogueFateCardService(regionProgress, affinity, regions);
    private final GameplayTickSlicer tickSlicer = new GameplayTickSlicer();

    private final ExplorationCompassService explorationCompass =
            new ExplorationCompassService(regionProgress, collectibles);
    private final MapMarkerService mapMarkers = new MapMarkerService(collectibles, regionProgress);
    private final ExplorationWorldImpactService explorationImpacts =
            new ExplorationWorldImpactService(regionProgress);
    private final RegionalTraverseService regionalTraverse = new RegionalTraverseService(traverse);
    private final ClimbRestPointService climbRestPoints = new ClimbRestPointService();
    private final UniversalTraversalService universalTraversal = new UniversalTraversalService(traverse);
    private final EcoNarrativeBridgeService ecoNarrative = new EcoNarrativeBridgeService(ecosystem);
    private final EnvironmentalStoryService environmentalStory = new EnvironmentalStoryService();
    private final WorldExplorationFeedbackService explorationFeedback = new WorldExplorationFeedbackService();
    private final CombatAssistService combatAssist = new CombatAssistService(hitFeedback);
    private final ResourceAutomationService resourceAutomation = new ResourceAutomationService();
    private final FlexibleDailyQuestService flexibleDaily = new FlexibleDailyQuestService();
    private final BuildRecommendationService buildRecommend = new BuildRecommendationService();

    private final ServerShadowService serverShadow = new ServerShadowService();
    private final ClientPredictedActionService clientPredict = new ClientPredictedActionService(serverShadow);
    private final TraverseMomentumService traverseMomentum = new TraverseMomentumService();
    private final DeterministicMutationService deterministicMutation = new DeterministicMutationService();
    private final ExplorationVitalityService explorationVitality = new ExplorationVitalityService(regionProgress);
    private final EcoMigrationScheduler ecoMigration = new EcoMigrationScheduler(regionProgress, ecosystem);
    private final OculiResonanceService oculiResonance = new OculiResonanceService(collectibles);
    private final CoopCampService coopCamp = new CoopCampService();
    private final PhantomBorrowService phantomBorrow = new PhantomBorrowService();
    private final SocialTokenService socialToken = new SocialTokenService();
    private final AffixShuffleService affixShuffle = new AffixShuffleService();
    private final MoveTrajectoryValidator moveTrajectory = new MoveTrajectoryValidator();
    private final InputConfidenceAnalyzer inputConfidence = new InputConfidenceAnalyzer();
    private final RegionHeatService regionHeat;
    private final WorldExplorationBalancer explorationBalancer;
    private final FightContributionPoolService fightContribution = new FightContributionPoolService();
    private final VisualSignificanceScheduler visualSignificance = new VisualSignificanceScheduler();
    private final CompanionGhostService companionGhost = new CompanionGhostService();
    private final WorldOwnershipContext worldOwnership = new WorldOwnershipContext();
    private final CoopRoomElectionService coopElection = new CoopRoomElectionService(worldOwnership, null);
    private final TerrainStateVector terrainStateVector = new TerrainStateVector();
    private final TerrainDetailTracker terrainDetailTracker = new TerrainDetailTracker();
    private final PhysicalDetailService physicalDetail = new PhysicalDetailService(terrainDetailTracker);
    private final GameTimeKeeper gameTimeKeeper = new GameTimeKeeper();
    private final CivilizationScheduleService civilizationSchedule = new CivilizationScheduleService();
    private final MassMomentumService massMomentum = new MassMomentumService();
    private final GrabThrowService grabThrow = new GrabThrowService(massMomentum);
    private final PerceptionModifierService perceptionModifier = new PerceptionModifierService();
    private final EcologicalTableauService ecologicalTableau = new EcologicalTableauService();
    private final CoopTerrainSnapshotService coopTerrainSnapshot = new CoopTerrainSnapshotService();

    public OpenWorldGameplayFacade() {
        regionHeat = new RegionHeatService(placement);
        explorationBalancer = new WorldExplorationBalancer(regionHeat, placement);
        explorationBalancer.bindRareElites(rareElites);
        ecoGraph.bind(regions, tugOfWar);
        companionGhost.bindPlayerStamina(stamina);
        movementAdmission.bindTerrainTracker(mutability.terrainTracker());
        movementAdmission.bindClimbRestPoints(climbRestPoints);
        movementAdmission.bindUniversalTraversal(universalTraversal);
        movementAdmission.bindTrajectoryValidator(moveTrajectory);
        movementAdmission.bindInputConfidence(inputConfidence);
        movementAdmission.bindTerrainStateVector(terrainStateVector);
        movementAdmission.bindCoopNarrative(coopNarrative);
        storyStateMachine.bindRegionLock(instanceRegionLock);
        storyStateMachine.bindCoopNarrative(coopNarrative);
        squadCommander.bindSiegeWar(siegeWar);
        terrainMutation.bindTerrainStateVector(terrainStateVector);
        collectibles.bindOwnership(worldOwnership);
        inputBuffer.bindPoise(poise);
        coopNarrative.bindSocialToken(socialToken);
        coopPuzzles.bindPhantomBorrow(phantomBorrow);
        coopElection.bind(worldOwnership, serverShadow);
        coopElection.bindTerrainSnapshot(coopTerrainSnapshot);
        physicalDetail.bindTerrainStateVector(terrainStateVector);
        gameTimeKeeper.bindServerEpoch(serverEpoch);
        civilizationSchedule.bindGameTimeKeeper(gameTimeKeeper);
        massMomentum.bindPhysicsAuthority(physicsAuthority);
        perceptionModifier.bindRegionImpact(regions);
        perceptionModifier.bindInputConfidence(inputConfidence);
        ecologicalTableau.bind(ecosystem, coopNarrative, chronicle);
        seedDemoContent();
    }

    public ExplorationRewardLoopService exploration() { return exploration; }
    public LandmarkWonderService landmarks() { return landmarks; }
    public RegionImpactService regions() { return regions; }
    public RegionProgressService regionProgress() { return regionProgress; }
    public CollectibleService collectibles() { return collectibles; }
    public GuidanceChainService guidanceChains() { return guidanceChains; }
    public StoryBranchService story() { return story; }
    public WorldEncounterService encounters() { return encounters; }
    public ExplorationSkillService exploreSkills() { return exploreSkills; }
    public TraverseModeService traverse() { return traverse; }
    public StaminaConsumeService stamina() { return stamina; }
    public GrappleNodeService grappleNodes() { return grappleNodes; }
    public MovementAdmissionService movementAdmission() { return movementAdmission; }
    public VehicleSyncService vehicles() { return vehicles; }
    public WorldSkillService worldSkills() { return worldSkills; }
    public EnvironmentInteractionService environment() { return environment; }
    public WorldSurpriseService surprises() { return surprises; }
    public OpenWorldHomesteadService homestead() { return homestead; }
    public HomelandService homeland() { return homeland; }
    public CreatureCatchService creatures() { return creatures; }
    public LeisureActivityService leisure() { return leisure; }
    public ExtractionMissionService extraction() { return extraction; }
    public WorldCookingService cooking() { return cooking; }
    public RuleTriggerService rules() { return rules; }
    public CoopPuzzleService coopPuzzles() { return coopPuzzles; }
    public WorldMutabilityService mutability() { return mutability; }
    public PhysicsLayerService physics() { return physics; }
    public ZoneLifecycleManager zones() { return zoneLifecycle; }
    public PuzzleTemplateService puzzles() { return puzzles; }
    public PublicMarkService publicMarks() { return publicMarks; }
    public RegionWorldChannelService regionChannel() { return regionChannel; }
    public ProceduralPlacementService placement() { return placement; }
    public OpenWorldConfigPatchService configPatch() { return configPatch; }
    public ReactionValidator reactions() { return reactions; }
    public BulletTimeService bulletTime() { return bulletTime; }
    public BattleReplayService replays() { return replays; }
    public PlayerChronicleService chronicle() { return chronicle; }
    public AscensionDungeonService ascension() { return ascension; }
    public UnderwaterPhysicsService underwater() { return underwater; }
    public FallAttackValidator fallAttack() { return fallAttack; }
    public HomelandGuardService homelandGuard() { return homelandGuard; }
    public CreatureUtilityService creatureUtility() { return creatureUtility; }
    public RareEliteSpawnService rareElites() { return rareElites; }
    public InputBufferService inputBuffer() { return inputBuffer; }
    public PoiseService poise() { return poise; }
    public BattleTriggerService battleTriggers() { return battleTriggers; }
    public ConstellationService constellation() { return constellation; }
    public SkillCastValidator skillCast() { return skillCast; }
    public RegionAwakeningService regionAwakening() { return regionAwakening; }
    public WorldCoreDungeonService worldCore() { return worldCore; }
    public EcosystemBehaviorService ecosystem() { return ecosystem; }
    public AffinityService affinity() { return affinity; }
    public GrapplePhysicsService grapplePhysics() { return grapplePhysics; }
    public VehicleCombatService vehicleCombat() { return vehicleCombat; }
    public ClimbAttackService climbAttack() { return climbAttack; }
    public TerrainMutationService terrainMutation() { return terrainMutation; }
    public ProjectileCurveService projectileCurve() { return projectileCurve; }
    public ServerEpochService serverEpoch() { return serverEpoch; }
    public RegionTugOfWarService tugOfWar() { return tugOfWar; }
    public DynamicCutsceneTrigger cutsceneTrigger() { return cutsceneTrigger; }
    public SiegeWarService siegeWar() { return siegeWar; }
    public PropTransformService propTransform() { return propTransform; }
    public AsymmetricPlayService asymmetricPlay() { return asymmetricPlay; }
    public CraftingTreeService crafting() { return crafting; }
    public AuctionHouseService auctionHouse() { return auctionHouse; }
    public HitFeedbackService hitFeedback() { return hitFeedback; }
    public DualClockService dualClock() { return dualClock; }
    public CombatEventRingBuffer combatEvents() { return combatEvents; }
    public BattleScenePodAllocator battleScenePods() { return battleScenePods; }
    public PrePlaybackService prePlayback() { return prePlayback; }
    public SquadCommanderService squadCommander() { return squadCommander; }
    public EnvironmentUtilizationAI envUtilAi() { return envUtilAi; }
    public TeamCompositionService teamComposition() { return teamComposition; }
    public InstanceRegionLockService instanceRegionLock() { return instanceRegionLock; }
    public StoryStateMachine storyStateMachine() { return storyStateMachine; }
    public HandbookService handbook() { return handbook; }
    public RogueFateCardService rogueFate() { return rogueFate; }
    public ExplorationCompassService explorationCompass() { return explorationCompass; }
    public MapMarkerService mapMarkers() { return mapMarkers; }
    public ExplorationWorldImpactService explorationImpacts() { return explorationImpacts; }
    public RegionalTraverseService regionalTraverse() { return regionalTraverse; }
    public ClimbRestPointService climbRestPoints() { return climbRestPoints; }
    public UniversalTraversalService universalTraversal() { return universalTraversal; }
    public EcoNarrativeBridgeService ecoNarrative() { return ecoNarrative; }
    public EnvironmentalStoryService environmentalStory() { return environmentalStory; }
    public WorldExplorationFeedbackService explorationFeedback() { return explorationFeedback; }
    public CombatAssistService combatAssist() { return combatAssist; }
    public ResourceAutomationService resourceAutomation() { return resourceAutomation; }
    public FlexibleDailyQuestService flexibleDaily() { return flexibleDaily; }
    public BuildRecommendationService buildRecommend() { return buildRecommend; }
    public ServerShadowService serverShadow() { return serverShadow; }
    public ClientPredictedActionService clientPredict() { return clientPredict; }
    public TraverseMomentumService traverseMomentum() { return traverseMomentum; }
    public DeterministicMutationService deterministicMutation() { return deterministicMutation; }
    public ExplorationVitalityService explorationVitality() { return explorationVitality; }
    public EcoMigrationScheduler ecoMigration() { return ecoMigration; }
    public OculiResonanceService oculiResonance() { return oculiResonance; }
    public CoopCampService coopCamp() { return coopCamp; }
    public PhantomBorrowService phantomBorrow() { return phantomBorrow; }
    public SocialTokenService socialToken() { return socialToken; }
    public AffixShuffleService affixShuffle() { return affixShuffle; }
    public MoveTrajectoryValidator moveTrajectory() { return moveTrajectory; }
    public InputConfidenceAnalyzer inputConfidence() { return inputConfidence; }
    public RegionHeatService regionHeat() { return regionHeat; }
    public WorldExplorationBalancer explorationBalancer() { return explorationBalancer; }
    public FightContributionPoolService fightContribution() { return fightContribution; }
    public VisualSignificanceScheduler visualSignificance() { return visualSignificance; }
    public PhysicsAuthorityService physicsAuthority() { return physicsAuthority; }
    public TerrainTopologyGraph terrainTopology() { return terrainTopology; }
    public EcoGraphService ecoGraph() { return ecoGraph; }
    public CoopNarrativeProxy coopNarrative() { return coopNarrative; }
    public CompanionGhostService companionGhost() { return companionGhost; }
    public WorldOwnershipContext worldOwnership() { return worldOwnership; }
    public CoopRoomElectionService coopElection() { return coopElection; }
    public TerrainStateVector terrainStateVector() { return terrainStateVector; }
    public TerrainDetailTracker terrainDetailTracker() { return terrainDetailTracker; }
    public PhysicalDetailService physicalDetail() { return physicalDetail; }
    public GameTimeKeeper gameTimeKeeper() { return gameTimeKeeper; }
    public CivilizationScheduleService civilizationSchedule() { return civilizationSchedule; }
    public MassMomentumService massMomentum() { return massMomentum; }
    public GrabThrowService grabThrow() { return grabThrow; }
    public PerceptionModifierService perceptionModifier() { return perceptionModifier; }
    public EcologicalTableauService ecologicalTableau() { return ecologicalTableau; }
    public CoopTerrainSnapshotService coopTerrainSnapshot() { return coopTerrainSnapshot; }

    public Map<String, Object> statusOverview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("explorationPoints", exploration.listPoints().size());
        body.put("landmarks", landmarks.list().size());
        body.put("regions", regions.listSnapshots());
        body.put("leisureZones", leisure.listZones().get("zones"));
        body.put("puzzleTemplates", puzzles.listTemplates().size());
        body.put("collectibles", collectibles.listAll().size());
        body.put("ruleTriggers", rules.listRules().size());
        body.put("epoch", serverEpoch.epochVersion());
        body.put("ecoCreatures", ecosystem.listSnapshots().size());
        body.put("p13", Map.of(
                "compassUnlockThreshold", ExplorationCompassService.UNLOCK_THRESHOLD_PERCENT,
                "regionalTraverseFacilities", regionalTraverse.listRegion("wolf-camp-valley").get("facilities"),
                "automationFacilities", 2,
                "flexibleDailyQuests", 3,
                "buildPresets", 2));
        body.put("p14", Map.of(
                "clientPredict", true,
                "serverShadowWindowMs", ServerShadowService.SHADOW_WINDOW_MS,
                "weeklyAffixCount", AffixShuffleService.WEEKLY_AFFIX_COUNT,
                "coopCampMaxParty", CoopCampService.MAX_PARTY_SIZE,
                "trajectoryWindow", MoveTrajectoryValidator.WINDOW_SIZE,
                "dotExitBufferMs", ZoneLifecycleManager.DOT_EXIT_BUFFER_MS,
                "regionHeatWindowMs", RegionHeatService.HEAT_WINDOW_MS,
                "enhancePityThreshold", 3));
        Map<String, Object> p16 = new LinkedHashMap<>();
        p16.put("physicsAuthority", true);
        p16.put("ecoGraph", true);
        p16.put("aimAssist", true);
        p16.put("explorationBalancer", true);
        p16.put("coopNarrative", true);
        p16.put("companionGhost", true);
        p16.put("shadowFastForward", true);
        p16.put("auctionStability", true);
        p16.put("predictionVerdict", true);
        p16.put("configSchemaVersioning", true);
        p16.put("physicsHashIntervalMs", PhysicsAuthorityService.CHECK_INTERVAL_MS);
        body.put("p16", p16);
        Map<String, Object> p17 = new LinkedHashMap<>();
        p17.put("worldOwnership", true);
        p17.put("hostOnlyAccess", true);
        p17.put("localBulletTime", true);
        p17.put("hostMigration", true);
        p17.put("terrainStateVector", true);
        p17.put("coopPuzzleLooseStrict", true);
        p17.put("syncCutscene", true);
        p17.put("hostMutexLock", true);
        p17.put("instanceLootBuckets", true);
        p17.put("hostDisconnectThresholdMs", CoopRoomElectionService.HOST_DISCONNECT_THRESHOLD_MS);
        p17.put("roomSnapshotTtlMs", ServerShadowService.ROOM_SNAPSHOT_TTL_MS);
        body.put("p17", p17);
        Map<String, Object> p18 = new LinkedHashMap<>();
        p18.put("actionStateMachine", true);
        p18.put("cancelPriority", true);
        p18.put("grapplePredictAck", true);
        p18.put("climbHangVault", true);
        p18.put("cameraRelativeMove", true);
        p18.put("iceSurfaceFriction", true);
        p18.put("dynamicInputWindow", true);
        p18.put("progressiveCollect", true);
        p18.put("airComboWindowMs", MovementAdmissionService.AIR_COMBO_WINDOW_MS);
        p18.put("shadowHeartbeatMs", ServerShadowService.HEARTBEAT_INTERVAL_MS);
        p18.put("grappleAuditDelayMs", PhysicsAuthorityService.GRAPPLE_AUDIT_DELAY_MS);
        p18.put("collectResumeMs", CollectibleService.COLLECT_RESUME_MS);
        body.put("p18", p18);
        Map<String, Object> p19 = new LinkedHashMap<>();
        p19.put("microPhysics", true);
        p19.put("civilizationSchedule", true);
        p19.put("massMomentum", true);
        p19.put("perceptionModifier", true);
        p19.put("ecologicalTableau", true);
        p19.put("coopTerrainSnapshot", true);
        p19.put("footprintTtlMs", TerrainDetailTracker.FOOTPRINT_TTL_MS);
        p19.put("debrisTtlMs", TerrainDetailTracker.DEBRIS_TTL_MS);
        p19.put("realMsPerGameHour", GameTimeKeeper.REAL_MS_PER_GAME_HOUR);
        p19.put("predictDeviationM", MassMomentumService.PREDICT_DEVIATION_METERS);
        p19.put("worldRecomposeWaitMs", CoopTerrainSnapshotService.WORLD_RECOMPOSE_WAIT_MS);
        body.put("p19", p19);
        Map<String, Object> p20 = new LinkedHashMap<>();
        p20.put("tickMicroPipeline", true);
        p20.put("movementBudgetMs", cn.itcast.demo.mymmorpg.ecs.SceneTickMicroPipeline.MOVEMENT_BUDGET_MS);
        p20.put("logicBudgetMs", cn.itcast.demo.mymmorpg.ecs.SceneTickMicroPipeline.LOGIC_BUDGET_MS);
        p20.put("syncBudgetMs", cn.itcast.demo.mymmorpg.ecs.SceneTickMicroPipeline.SYNC_BUDGET_MS);
        p20.put("broadcastImportanceFuse", true);
        p20.put("clientTimestampBufferMs", ReactionValidator.CLIENT_TIMESTAMP_BUFFER_MS);
        p20.put("terrainWriteDelayMs", TerrainStateVector.TERRAIN_WRITE_DELAY_MS);
        p20.put("dualClock", true);
        p20.put("combatEventRingBuffer", true);
        p20.put("maxProjectiles", CombatEventRingBuffer.MAX_PROJECTILES);
        p20.put("battleScenePodAllocator", true);
        p20.put("redisStationarySkipMs", cn.itcast.demo.mymmorpg.cache.RedisPositionBatchWriter.STATIONARY_SKIP_MS);
        body.put("p20", p20);
        return body;
    }

    /** 发现探索点：自动把当前角色探索技能同步到奖励循环门槛。 */
    public Map<String, Object> discoverExploration(
            long playerId, String pointId, float x, float y, float z, long nowMs) {
        syncExploreSkills(playerId);
        Map<String, Object> r = exploration.discover(playerId, pointId, x, y, z, nowMs);
        if (Boolean.TRUE.equals(r.get("ok")) && r.get("exploreScore") instanceof Number score) {
            landmarks.setExploreScore(playerId, score.intValue());
        }
        return r;
    }

    /** 进入功能奇观：校验已解锁移动手段与入口条件。 */
    public Map<String, Object> enterLandmark(long playerId, String landmarkId) {
        return landmarks.enter(playerId, landmarkId, traverse.modeNames(playerId));
    }

    /**
     * 环境交互并联动 ECA 规则触发器（如：火+藤蔓+夜晚 → 隐藏路径）。
     */
    public Map<String, Object> envInteractWithRules(
            String objectId,
            EnvironmentInteractionService.SkillElement skill,
            long playerId,
            int hourOfDay,
            long nowMs) {
        Map<String, Object> env = environment().interact(objectId, skill, playerId);
        if (!Boolean.TRUE.equals(env.get("ok"))) {
            return env;
        }
        EnvironmentInteractionService.EnvObject obj = environment().get(objectId);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("element", skill == null ? "PHYSICAL" : skill.name());
        ctx.put("target", obj == null ? "" : obj.element().name());
        ctx.put("time", (hourOfDay >= 19 || hourOfDay <= 5) ? "NIGHT" : "DAY");
        ctx.put("objectId", objectId);
        ctx.put("playerId", playerId);
        ctx.put("reaction", env.get("reaction"));
        Map<String, Object> triggered = rules().fire("INTERACT", ctx);
        Map<String, Object> body = new LinkedHashMap<>(env);
        body.put("ruleTrigger", triggered);
        if ("ASH".equals(env.get("to")) && obj != null) {
            body.put("physics", physics().apply(
                    1, obj.x(), obj.z(),
                    PhysicsLayerService.PhysicsKind.FIRE, 2, 12_000L, nowMs));
        }
        if ("FROZEN".equals(env.get("to")) && obj != null) {
            body.put("physics", physics().freezeSurface(1, obj.x(), obj.z(), 15_000L, nowMs));
        }
        return body;
    }

    /** 带动态品质的收集物领取；若开启跟随采集则叠加生物亲和加成。 */
    public Map<String, Object> collectWithDynamicLoot(
            long playerId, String collectibleId, float x, float y, float z,
            int worldLevel, float regionExplorationRate) {
        return collectWithDynamicLoot(playerId, collectibleId, x, y, z,
                worldLevel, regionExplorationRate, null);
    }

    public Map<String, Object> collectWithDynamicLoot(
            long playerId, String collectibleId, float x, float y, float z,
            int worldLevel, float regionExplorationRate, String coopRoomId) {
        Map<String, Object> base = collectibles.collect(playerId, collectibleId, x, y, z,
                new CollectibleService.DynamicLootTier(worldLevel, regionExplorationRate), coopRoomId);
        return creatureUtility.applyHarvestBonus(playerId, base);
    }

    /** 收集物领取并联动 P13：区域进度、地图标记、探索度世界影响。 */
    public Map<String, Object> collectWithExplorationFeedback(
            long playerId, String regionId, String collectibleId,
            float x, float y, float z, int worldLevel, float regionExplorationRate) {
        return collectWithExplorationFeedback(
                playerId, regionId, collectibleId, x, y, z, worldLevel, regionExplorationRate, null);
    }

    public Map<String, Object> collectWithExplorationFeedback(
            long playerId, String regionId, String collectibleId,
            float x, float y, float z, int worldLevel, float regionExplorationRate, String coopRoomId) {
        Map<String, Object> base = collectWithDynamicLoot(
                playerId, collectibleId, x, y, z, worldLevel, regionExplorationRate, coopRoomId);
        if (!Boolean.TRUE.equals(base.get("ok"))) {
            return base;
        }
        if (regionId != null && !regionId.isBlank()) {
            regionProgress.markCollectible(playerId, regionId, collectibleId);
            Map<String, Object> body = new LinkedHashMap<>(base);
            body.put("map_markers", mapMarkers.markersForRegion(playerId, regionId).get("summary"));
            body.put("exploration_impacts", explorationImpacts.evaluate(playerId, regionId));
            body.put("compass", explorationCompass.unlockStatus(playerId, regionId));
            return body;
        }
        return base;
    }

    /** 大世界玩法层定时 tick：区域潮汐衰退 + 生态行为。 */
    public Map<String, Object> tickGameplay(long nowMs) {
        List<Map<String, Object>> decayed = new java.util.ArrayList<>();
        List<Map<String, Object>> regionSnaps = regions.listSnapshots();
        var sliceResult = tickSlicer.processSlice(
                regionSnaps,
                snap -> {
                    Object id = snap.get("regionId");
                    return id == null ? 0L : String.valueOf(id).hashCode();
                },
                (snap, ts) -> {
                    Object id = snap.get("regionId");
                    if (id == null) {
                        return;
                    }
                    Map<String, Object> r = regions.tickDecay(String.valueOf(id), ts);
                    if (Boolean.TRUE.equals(r.get("decayed"))) {
                        decayed.add(r);
                    }
                },
                nowMs);
        // 生态 AI 5s Tick 异步化，不阻塞主场景线程
        tickSlicer.runAsyncFireAndForget(ts -> ecosystem.tickRegion("wolf-camp-valley", 12, ts), nowMs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("decayedRegions", decayed);
        body.put("decayedCount", decayed.size());
        body.put("slice", Map.of(
                "index", sliceResult.sliceIndex(),
                "processed", sliceResult.processedCount(),
                "elapsedMs", sliceResult.elapsedMs(),
                "withinBudget", sliceResult.withinBudget()));
        body.put("tickSlicer", tickSlicer.stats());
        body.put("atMs", nowMs);
        return body;
    }

    public GameplayTickSlicer tickSlicer() {
        return tickSlicer;
    }

    public Map<String, Object> gameplayStatusWithRegion(long playerId, String regionId) {
        Map<String, Object> body = new LinkedHashMap<>(statusOverview());
        if (regionId != null && !regionId.isBlank()) {
            body.put("region_progress", regionProgress.status(playerId, regionId));
            body.put("compass", explorationCompass.unlockStatus(playerId, regionId));
            body.put("map_markers_summary", mapMarkers.markersForRegion(playerId, regionId).get("summary"));
            body.put("exploration_impacts", explorationImpacts.evaluate(playerId, regionId));
            body.put("universal_traversal", universalTraversal.snapshot(playerId));
            body.put("combat_assist", combatAssist.snapshot(playerId));
            body.put("resource_automation", resourceAutomation.status(playerId));
            body.put("flexible_daily", flexibleDaily.list(playerId));
            body.put("build_recommendation", buildRecommend.snapshot(playerId));
        } else {
            body.put("region_progress", regionProgress.listPlayerRegions(playerId));
        }
        return body;
    }

    private void syncExploreSkills(long playerId) {
        Map<String, Object> snap = exploreSkills.snapshot(playerId);
        Object skills = snap.get("skills");
        if (skills instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> m && m.get("id") != null) {
                    exploration.grantExploreSkill(playerId, String.valueOf(m.get("id")));
                }
            }
        }
    }

    private void seedDemoContent() {
        exploration.register(new ExplorationPoint(
                "vista-sky-ruin", 1, 1, ExplorationContentKind.VISTA,
                200f, 40f, 120f, 8f,
                List.of(Map.of("itemId", "primogem", "count", 10),
                        Map.of("itemId", "char_exp", "count", 5)),
                null, true));
        exploration.register(new ExplorationPoint(
                "chest-hidden-glyph", 1, 1, ExplorationContentKind.CHEST,
                310f, 2f, 88f, 4f,
                List.of(Map.of("itemId", "skin_cape_explorer", "count", 1)),
                "TREASURE_SENSE", true));
        exploration.register(new ExplorationPoint(
                "puzzle-wind-obelisk", 1, 1, ExplorationContentKind.PUZZLE,
                450f, 5f, 200f, 5f,
                List.of(Map.of("itemId", "gacha_ticket", "count", 1)),
                null, true));

        landmarks.register(new LandmarkWonder(
                "floating-ruin", "悬浮古代遗迹", 1, 1,
                500f, 80f, 500f, 3,
                List.of("HOOK", "GLIDE", "WIND_FIELD"),
                17,
                List.of(Map.of("itemId", "primogem", "count", 40),
                        Map.of("itemId", "weapon_asc_mat", "count", 3)),
                List.of("portal:ruin-peak", "npc:ruin-scholar", "shop:relic-exchange")));
        landmarks.register(new LandmarkWonder(
                "sealed-sanctum", "封印圣所", 1, 1,
                520f, 60f, 480f, 2,
                List.of("CLIMB"),
                18,
                List.of(Map.of("itemId", "primogem", "count", 20)),
                List.of("portal:sanctum")));
        landmarks.setEntryCondition(new LandmarkWonderService.WonderEntryCondition(
                "sealed-sanctum", List.of("puzzle-demo-obelisk"), 0));

        regions.register(new RegionImpactService.RegionProfile(
                "wolf-camp-valley", "狼营谷", 1, 3,
                List.of("npc:rescued-merchant", "shop:valley-supplies", "portal:valley-waypoint"),
                60_000L, "boss-geo-hypostasis"));
        regions.registerEventChain("wolf-camp-valley", List.of(
                new RegionImpactService.EventChainStep(
                        "caravan-depart", "商队从谷口出发", "escort-ambush",
                        Map.of("from", "B", "mission", "escort")),
                new RegionImpactService.EventChainStep(
                        "escort-ambush", "护送途中遭遇伏击", "unlock-portal-c",
                        Map.of("battle", "dynamic_ambush")),
                new RegionImpactService.EventChainStep(
                        "unlock-portal-c", "解锁 C 点传送", null,
                        Map.of("portal", "portal:valley-c"))));

        regionProgress.register(new RegionProgressService.RegionMeta(
                "wolf-camp-valley", "狼营谷", 5, 8, 4, 3,
                List.of(
                        new RegionProgressService.ReputationTier(40, "wing_breeze", "微风之翼",
                                List.of(Map.of("itemId", "wing_breeze", "count", 1))),
                        new RegionProgressService.ReputationTier(80, "card_valley", "谷地名片",
                                List.of(Map.of("itemId", "namecard_valley", "count", 1),
                                        Map.of("itemId", "recipe_valley_stew", "count", 1))))));

        collectibles.register(new CollectibleService.CollectibleDef(
                "chest-common-1", "路边木箱", CollectibleService.Tier.COMMON_CHEST,
                1, 100f, 0f, 100f, 4f, "gold", 50, 0, 0));
        collectibles.register(new CollectibleService.CollectibleDef(
                "chest-fine-1", "精致宝箱", CollectibleService.Tier.FINE_CHEST,
                1, 150f, 0f, 120f, 4f, "gacha_ticket", 1, 0, 0));
        collectibles.register(new CollectibleService.CollectibleDef(
                "oculus-anemo-1", "风神瞳", CollectibleService.Tier.OCULUS,
                1, 180f, 20f, 160f, 5f, "oculus_fragment", 1, 1, 20));
        collectibles.register(new CollectibleService.CollectibleDef(
                "chest-hidden-1", "隐藏宝箱", CollectibleService.Tier.PRECIOUS_CHEST,
                1, 105f, 0f, 105f, 4f, "primogem", 5, 0, 0));
        collectibles.setVisibility("chest-hidden-1", CollectibleService.Visibility.HIDDEN);

        guidanceChains.register(new GuidanceChainService.GuideChainDef(
                "seelie-chest-1", "wolf-camp-valley", "chest-fine-1",
                List.of(
                        new GuidanceChainService.GuideNode("g-start", GuidanceChainService.NodeKind.START,
                                140f, 0f, 100f, 5f),
                        new GuidanceChainService.GuideNode("g-seelie", GuidanceChainService.NodeKind.SEELIE,
                                145f, 2f, 110f, 4f),
                        new GuidanceChainService.GuideNode("g-ring", GuidanceChainService.NodeKind.WIND_RING,
                                148f, 4f, 115f, 4f),
                        new GuidanceChainService.GuideNode("g-chest", GuidanceChainService.NodeKind.CHEST,
                                150f, 0f, 120f, 4f))));

        regions.registerCleanseAnchor(new RegionImpactService.CleanseAnchor(
                "cleanse-valley-1", "wolf-camp-valley", 200f, 0f, 200f, 8f, 120_000L));

        movementAdmission.registerClimbable(new MovementAdmissionService.ClimbableMesh(
                "cliff-valley-north", 1, 400f, 30f, 200f, 12f, 10f));
        movementAdmission.registerWindField(new MovementAdmissionService.WindField(
                "wind-peak-1", 1, 500f, 80f, 500f, 40f, 10f));
        grappleNodes.register(new GrappleNodeService.GrappleNode(
                "grapple-ruin-1", 1, 480f, 60f, 490f, 4f, 1500L));
        vehicles.register(new VehicleSyncService.VehicleDef(
                "boat-lake-1", "WATER", 14f, 4f, 100f, 1.5f, 100));
        vehicles.register(new VehicleSyncService.VehicleDef(
                "cart-coast-1", "LAND", 18f, 6f, 120f, 2f, 100));

        mutability.register(new WorldMutabilityService.DestroyableDef(
                "tree-valley-1", "TREE", 1, 115f, 0f, 95f, 80, 120_000L, false));
        mutability.register(new WorldMutabilityService.DestroyableDef(
                "bridge-plank-1", "BRIDGE", 1, 300f, 2f, 300f, 150, 600_000L, true));
        mutability.register(new WorldMutabilityService.DestroyableDef(
                "ore-rock-1", "ORE", 1, 250f, 0f, 180f, 100, 180_000L, true));
        mutability.register(new WorldMutabilityService.DestroyableDef(
                "cliff-wall-1", "CLIFF", 1, 410f, 20f, 205f, 120, 600_000L, true));

        coopPuzzles.register(new CoopPuzzleService.PuzzleDef(
                "pressure-duo-1", "zone-valley-a", 2, "gadget-pressure-1"));

        chronicle.register(new PlayerChronicleService.ChoiceNode(
                "choice-dragon-fate", 0, "spare_dragon", "slay_dragon"));

        creatures.registerTemplate(new CreatureCatchService.CreatureTemplate(
                "crystal_fox", 14f, 10f, 1.15f, 21001));
        creatures.spawn(new CreatureCatchService.WildCreature(
                "wild-slime-1", "anemo_slime", 1, 220f, 0f, 180f, 35, 12, 80));
        creatures.spawn(new CreatureCatchService.WildCreature(
                "wild-fox-1", "crystal_fox", 1, 260f, 0f, 210f, 55, 18, 120, 40));

        ascension.register(new AscensionDungeonService.AscensionQuest(
                "asc-wl-1-to-2", 1, 2, "dungeon-ascension-wl2", 180_000L));

        story.register(new StoryBranchService.StoryNode(
                "story-open-1", "旅人的抉择", "路边的旅伴请你帮忙寻找失散的妹妹。",
                List.of(
                        new StoryBranchService.StoryNode.Choice(
                                "help", "立刻帮忙", "story-help-path",
                                Map.of("affinity_ayaka", 10, "route", "kind")),
                        new StoryBranchService.StoryNode.Choice(
                                "later", "先继续探索", "story-explore-path",
                                Map.of("affinity_ayaka", -2, "route", "free")))));
        story.register(new StoryBranchService.StoryNode(
                "story-help-path", "同行之路", "你们一起找到了线索……", List.of()));
        story.register(new StoryBranchService.StoryNode(
                "story-explore-path", "自由之路", "她留下一句「有缘再见」。", List.of()));

        encounters.register(new WorldEncounterService.EncounterDef(
                "meet-ayaka-scenic", "char_ayaka", "神里绫华",
                WorldEncounterService.EncounterKind.SCENIC,
                "她邀请你共赏瀑布落日。", 5, 120,
                Map.of("affinity", 5)));
        encounters.register(new WorldEncounterService.EncounterDef(
                "meet-bennett-commission", "char_bennett", "班尼特",
                WorldEncounterService.EncounterKind.COMMISSION,
                "「冒险家协会的委托，要不要一起？」", 8, 90,
                Map.of("commissionId", "daily_collect_3")));

        exploreSkills.bindCharacterSkills("char_ayaka",
                Set.of(ExplorationSkillService.ExploreSkill.ANCIENT_TRANSLATE,
                        ExplorationSkillService.ExploreSkill.NIGHT_VISION));
        exploreSkills.bindCharacterSkills("char_qiqi",
                Set.of(ExplorationSkillService.ExploreSkill.TREASURE_SENSE,
                        ExplorationSkillService.ExploreSkill.ELEMENT_TRACE));
        exploreSkills.bindCharacterSkills("char_venti",
                Set.of(ExplorationSkillService.ExploreSkill.WIND_READ,
                        ExplorationSkillService.ExploreSkill.TREASURE_SENSE));

        worldSkills.bindProfile(new WorldSkillService.CharacterWorldProfile(
                "char_venti",
                Set.of(WorldSkillService.WorldSkillType.GLIDE_SPEED_UP,
                        WorldSkillService.WorldSkillType.HOOK_RANGE_UP),
                Set.of(TraverseModeService.Mode.GLIDE, TraverseModeService.Mode.WIND_FIELD,
                        TraverseModeService.Mode.HOOK),
                null));
        worldSkills.bindProfile(new WorldSkillService.CharacterWorldProfile(
                "char_kazuha",
                Set.of(WorldSkillService.WorldSkillType.CLIMB_STAMINA_REDUCE,
                        WorldSkillService.WorldSkillType.GLIDE_SPEED_UP),
                Set.of(TraverseModeService.Mode.GLIDE, TraverseModeService.Mode.CLIMB),
                null));
        worldSkills.bindProfile(new WorldSkillService.CharacterWorldProfile(
                "char_zhongli",
                Set.of(WorldSkillService.WorldSkillType.ORE_SPECIALIST),
                Set.of(TraverseModeService.Mode.CLIMB),
                "item_geo_sigil"));

        environment.register(new EnvironmentInteractionService.EnvObject(
                "vine-barrier-1", EnvironmentInteractionService.EnvElement.VINE,
                "BLOCKING", 1, 120f, 0f, 60f));
        environment.register(new EnvironmentInteractionService.EnvObject(
                "throw-stone-1", EnvironmentInteractionService.EnvElement.STONE,
                "IDLE", 1, 130f, 0f, 70f));
        environment.register(new EnvironmentInteractionService.EnvObject(
                "oil-patch-1", EnvironmentInteractionService.EnvElement.OIL,
                "IDLE", 1, 140f, 0f, 55f));

        surprises.register(new WorldSurpriseService.SurpriseDef(
                "cave-behind-waterfall", "瀑布后的隐藏洞穴",
                WorldSurpriseService.TriggerType.PROXIMITY, null,
                90f, 1f, 20f, 6f, "primogem", 15));
        surprises.register(new WorldSurpriseService.SurpriseDef(
                "emote-statue-dance", "雕像搞怪小游戏",
                WorldSurpriseService.TriggerType.ACTION_EMOTE, "DANCE",
                300f, 0f, 300f, 8f, "leisure_coin", 5));

        homestead.registerPlot(new OpenWorldHomesteadService.Plot(
                "plot-seaside-1", 1, 50f, 0f, 50f, 40f));

        leisure.registerZone(new LeisureActivityService.Zone(
                "fish-lake-1", LeisureActivityService.LeisureType.FISHING,
                1, 80f, 40f, 25f));
        leisure.registerZone(new LeisureActivityService.Zone(
                "race-coast-1", LeisureActivityService.LeisureType.RACING,
                1, 400f, 100f, 60f));
        leisure.registerZone(new LeisureActivityService.Zone(
                "rhythm-plaza-1", LeisureActivityService.LeisureType.RHYTHM,
                1, 150f, 150f, 20f));

        extraction.register(new ExtractionMissionService.MissionDef(
                "extract-ruin-cache", "遗迹物资撤离",
                1, 600f, 600f, 650f, 650f, 580f, 580f, 15f,
                List.of(Map.of("itemId", "primogem", "count", 25),
                        Map.of("itemId", "gacha_ticket", "count", 2))));

        cooking.registerCampfire(new WorldCookingService.Campfire(
                "camp-valley-1", 1, 110f, 0f, 90f, 8f));
        cooking.registerRecipe(new WorldCookingService.Recipe(
                "recipe-sweet-madame", "甜甜花酿鸡",
                Map.of("fowl", 2, "sweet_flower", 2),
                "atk_up_small", 300, "food_sweet_madame"));
        cooking.registerRecipe(new WorldCookingService.Recipe(
                "recipe-mint-jelly", "薄荷果冻",
                Map.of("mint", 3),
                "stamina_recover", 120, "food_mint_jelly"));

        rules.register(new RuleTriggerService.RuleDef(
                "rule-burn-vine-night", "vine-barrier-1", "INTERACT",
                RuleTriggerService.LogicOp.AND,
                List.of(
                        new RuleTriggerService.Condition("element", "EQ", "FIRE"),
                        new RuleTriggerService.Condition("target", "EQ", "VINE"),
                        new RuleTriggerService.Condition("time", "EQ", "NIGHT")),
                List.of(new RuleTriggerService.Action("REVEAL_PATH", "hidden-path-1",
                        Map.of("pathId", "night-vine-path")),
                        new RuleTriggerService.Action("SET_STATE", "vine-barrier-1",
                                Map.of("state", "ASH"))),
                true));
        rules.register(new RuleTriggerService.RuleDef(
                "rule-arrow-wind", "hover-target-1", "HIT",
                RuleTriggerService.LogicOp.AND,
                List.of(
                        new RuleTriggerService.Condition("weapon", "EQ", "BOW"),
                        new RuleTriggerService.Condition("target", "EQ", "HOVER_TARGET")),
                List.of(new RuleTriggerService.Action("SPAWN_WIND", "wind-field-1",
                        Map.of("power", 2))),
                true));
        rules.register(new RuleTriggerService.RuleDef(
                "rule-coop-pressure", "gadget-pressure-1", "PRESS_PLATE",
                RuleTriggerService.LogicOp.AND,
                List.of(new RuleTriggerService.Condition("party_members", "REQUIRE_PARTY_MEMBERS", "2")),
                List.of(new RuleTriggerService.Action("SET_STATE", "gadget-pressure-1",
                        Map.of("state", "ACTIVE")),
                        new RuleTriggerService.Action("SPAWN_WIND", "coop-wind-1", Map.of("power", 3))),
                true));

        puzzles.instantiateFromJson(Map.of(
                "puzzleId", "puzzle-demo-obelisk",
                "type", "ELEMENT_OBELISK",
                "worldId", 1, "sceneId", 1,
                "x", 450f, "y", 5f, "z", 200f,
                "config", Map.of("sequence", List.of("FIRE", "CRYO", "ANEMO")),
                "rewards", List.of(Map.of("itemId", "gacha_ticket", "count", 1))));
        puzzles.instantiateFromJson(Map.of(
                "puzzleId", "puzzle-demo-target",
                "type", "SHOOTING_TARGET",
                "x", 460f, "y", 10f, "z", 210f,
                "config", Map.of("targetCount", 3),
                "rewards", List.of(Map.of("itemId", "primogem", "count", 5))));

        configPatch.upsert("1:3:4", "CHEST", Map.of("chestId", "chest-fine-1", "x", 150, "z", 120));

        // P9：命座机制覆盖 / 生物采集亲和 / 水下区域
        constellation.registerOverride(new ConstellationService.ConstellationBuffOverride(
                "char-warden", 1, "skill-evil-warding", 6_000, 3, true, "EvilWarding"));
        skillCast.registerSkill(new SkillCastValidator.SkillBase(
                "skill-evil-warding", 12_000, 1, 1, 40));
        creatureUtility.setHarvestAffinity("crystal_fox", 0.2);
        underwater.markRegionBiome(1, UnderwaterPhysicsService.BIOME_UNDERWATER);

        ecosystem.register(new EcosystemBehaviorService.CreatureEcoProfile(
                "eco-fox-1", "crystal_fox", "wolf-camp-valley", 260f, 0f, 210f, true, 12f));
        ecosystem.register(new EcosystemBehaviorService.CreatureEcoProfile(
                "eco-boar-1", "boar", "wolf-camp-valley", 240f, 0f, 190f, true, 10f));
        vehicleCombat.register(new VehicleCombatService.VehicleCombatDef(
                "cart-coast-1", List.of("vehicle_ram", "vehicle_cannon"), 120f));
        terrainMutation.markWater("wolf-camp-valley", 10, 10);
        terrainMutation.markWater("wolf-camp-valley", 10, 11);
        terrainMutation.setHumidity("wolf-camp-valley", 75f);
        crafting.register(new CraftingTreeService.RecipeNode(
                "iron_ingot", "iron_ingot", 1, Map.of("iron_ore", 3), 2_000L, 0.2, true, false));
        crafting.register(new CraftingTreeService.RecipeNode(
                "sword_blank", "sword_blank", 1, Map.of("iron_ingot", 2), 5_000L, 0.15, false, true));
        squadCommander.register(new SquadCommanderService.SquadTemplate(
                "squad-archer-1", 9001L, List.of(9002L, 9003L, 9004L, 9005L)));
        envUtilAi.registerThrowable("boulder-1", 200f, 200f, 0.2);

        // P11：元素共鸣 / 传说任务 / 图鉴 / 弹射蘑菇
        storyStateMachine.register(new StoryStateMachine.StoryDef(
                "legend-ayaka-1", "char_ayaka", "wolf-camp-valley", "神里的邀约",
                List.of(
                        new StoryStateMachine.DialogueOption(
                                "accept", "答应同行", 15, "前往狼营谷护送"),
                        new StoryStateMachine.DialogueOption(
                                "decline", "婉拒改日", -2, "好感微降")),
                20));
        handbook.registerCatalog(HandbookService.EntryKind.CREATURE, "crystal_fox");
        handbook.registerCatalog(HandbookService.EntryKind.CREATURE, "anemo_slime");
        handbook.registerCatalog(HandbookService.EntryKind.CREATURE, "boar");
        handbook.registerCatalog(HandbookService.EntryKind.CREATURE, "crystal_butterfly");
        handbook.registerCatalog(HandbookService.EntryKind.CREATURE, "snow_fox");
        handbook.registerCatalog(HandbookService.EntryKind.FISH, "sweet_flower_fish");
        handbook.registerCatalog(HandbookService.EntryKind.FISH, "medaka");
        handbook.registerCatalog(HandbookService.EntryKind.FOOD, "food_sweet_madame");
        handbook.registerCatalog(HandbookService.EntryKind.FOOD, "food_mint_jelly");

        // P13：探索便利 / 移动体验 / 生态沉浸 / 战斗辅助 / 长线减负
        mapMarkers.syncFromCollectibles();
        mapMarkers.register(new MapMarkerService.MarkerDef(
                "challenge-timed-1", MapMarkerService.MarkerKind.CHALLENGE,
                "wolf-camp-valley", "限时挑战·风圈穿越", 148f, 4f, 115f));
        explorationCompass.registerTimedChallenge(new ExplorationCompassService.TimedChallengeDef(
                "challenge-timed-1", "wolf-camp-valley", "限时挑战·风圈穿越", 148f, 4f, 115f, 8f));
        explorationImpacts.register(new ExplorationWorldImpactService.ImpactDef(
                "impact-path-50", "wolf-camp-valley", 50,
                ExplorationWorldImpactService.ImpactKind.UNLOCK_PATH, "hidden-path-1",
                "探索过半，隐秘山道显现",
                Map.of("pathId", "valley-shortcut-east")));
        explorationImpacts.register(new ExplorationWorldImpactService.ImpactDef(
                "impact-npc-70", "wolf-camp-valley", 70,
                ExplorationWorldImpactService.ImpactKind.NPC_DIALOGUE, "npc:rescued-merchant",
                "商人认出你的探索足迹",
                Map.of("dialogueLine", "多亏你清理了狼营，商路又通了！")));
        explorationImpacts.register(new ExplorationWorldImpactService.ImpactDef(
                "impact-event-90", "wolf-camp-valley", 90,
                ExplorationWorldImpactService.ImpactKind.TRIGGER_EVENT, "valley-festival",
                "谷地庆典触发",
                Map.of("eventId", "valley_harvest_festival")));
        regionalTraverse.register(new RegionalTraverseService.FacilityDef(
                "zipline-valley-1", "wolf-camp-valley", RegionalTraverseService.FacilityKind.ZIPLINE,
                200f, 30f, 200f, 280f, 10f, 220f, 6f, 2.2f, null));
        regionalTraverse.register(new RegionalTraverseService.FacilityDef(
                "wind-current-peak", "wolf-camp-valley", RegionalTraverseService.FacilityKind.WIND_CURRENT,
                500f, 60f, 500f, 520f, 90f, 480f, 10f, 1.8f, TraverseModeService.Mode.GLIDE));
        climbRestPoints.register(new ClimbRestPointService.RestPoint(
                "rest-cliff-1", "cliff-valley-north", 405f, 25f, 202f, 4f, 40f, true));
        ecoNarrative.register(new EcoNarrativeBridgeService.EcoStoryLink(
                "eco-fox-feed", "crystal_fox", EcosystemBehaviorService.EcoState.FEED,
                "quest-fox-trail", "story-fox-clue", "npc:rescued-merchant",
                "水晶狐正在进食，或许留下了宝藏线索……"));
        environmentalStory.register(new EnvironmentalStoryService.StoryProp(
                "prop-abandoned-camp", "wolf-camp-valley", "废弃营地",
                "熄灭的篝火旁散落着未收好的行囊，主人似乎走得很匆忙。",
                230f, 0f, 195f, EnvironmentalStoryService.PropState.DISTURBED,
                List.of("ambush", "merchant", "wolf")));
        explorationFeedback.register(new WorldExplorationFeedbackService.FeedbackRule(
                "fb-clear-camp", "wolf-camp-valley", WorldExplorationFeedbackService.ActionKind.CLEAR_CAMP,
                1, "清理狼营后，谷口捷径永久开放",
                Map.of("unlockShortcut", "portal:valley-shortcut", "envBloom", true)));
        resourceAutomation.register(new ResourceAutomationService.FacilityDef(
                "auto-mine-1", "自动矿机", "iron_ore", 12, 500, 3));
        resourceAutomation.register(new ResourceAutomationService.FacilityDef(
                "auto-farm-1", "自动农场", "sweet_flower", 20, 300, 3));
        flexibleDaily.register(new FlexibleDailyQuestService.QuestDef(
                "daily-stamina", "消耗体力", FlexibleDailyQuestService.ProgressKind.STAMINA_SPENT, 100,
                List.of(Map.of("itemId", "primogem", "count", 20))));
        flexibleDaily.register(new FlexibleDailyQuestService.QuestDef(
                "daily-battle", "完成任意战斗", FlexibleDailyQuestService.ProgressKind.ANY_BATTLE, 3,
                List.of(Map.of("itemId", "mora", "count", 5000))));
        flexibleDaily.register(new FlexibleDailyQuestService.QuestDef(
                "daily-explore", "探索发现", FlexibleDailyQuestService.ProgressKind.EXPLORE_DISCOVER, 5,
                List.of(Map.of("itemId", "gacha_ticket", "count", 1))));
        buildRecommend.register(new BuildRecommendationService.BuildPreset(
                "preset-ayaka-dps", "char_ayaka", "绫华·冰伤爆发",
                "weapon_sword_mistsplitter",
                List.of("blizzard_strayer", "blizzard_strayer"),
                Map.of("sand", "atk_pct", "goblet", "cryo_dmg", "circlet", "crit_dmg"),
                List.of("crit_rate", "crit_dmg", "atk_pct", "energy_recharge")));
        buildRecommend.register(new BuildRecommendationService.BuildPreset(
                "preset-bennett-support", "char_bennett", "班尼特·增伤辅助",
                "weapon_sword_aquila",
                List.of("noblesse_oblige", "crimson_witch"),
                Map.of("sand", "energy_recharge", "goblet", "pyro_dmg", "circlet", "hp_pct"),
                List.of("energy_recharge", "hp_pct", "atk_pct")));

        // P19：微观物理 / 文明作息 / 质量动量 / 生态纪录片
        ecosystem.register(new EcosystemBehaviorService.CreatureEcoProfile(
                "eco-wolf-1", "wolf_predator", "wolf-camp-valley", 242f, 0f, 192f, false, 15f));
        ecosystem.register(new EcosystemBehaviorService.CreatureEcoProfile(
                "eco-rabbit-1", "rabbit_prey", "wolf-camp-valley", 244f, 0f, 194f, true, 8f));
        civilizationSchedule.registerNpc(
                new CivilizationScheduleService.NpcBaseState(
                        "npc-guard-valley", "guard", 220f, 0f, 180f, false),
                new CivilizationScheduleService.ScheduleTemplate("npc-guard-valley", "wolf-camp-valley", List.of(
                        new CivilizationScheduleService.ScheduleSlot(
                                360, 1320, 0f, 0f, 0f,
                                CivilizationScheduleService.NpcAction.PATROL, "保持警戒。"),
                        new CivilizationScheduleService.ScheduleSlot(
                                1320, 1440, 0f, 0f, 0f,
                                CivilizationScheduleService.NpcAction.SLEEP, "……Zzz"),
                        new CivilizationScheduleService.ScheduleSlot(
                                0, 360, 0f, 0f, 0f,
                                CivilizationScheduleService.NpcAction.SLEEP, "……Zzz"))));
        civilizationSchedule.registerNpc(
                new CivilizationScheduleService.NpcBaseState(
                        "npc-shop-valley", "merchant", 225f, 0f, 185f, true),
                new CivilizationScheduleService.ScheduleTemplate("npc-shop-valley", "wolf-camp-valley", List.of(
                        new CivilizationScheduleService.ScheduleSlot(
                                480, 1320, 0f, 0f, 0f,
                                CivilizationScheduleService.NpcAction.TRADE, "欢迎光临！"),
                        new CivilizationScheduleService.ScheduleSlot(
                                1320, 480, 2f, 0f, 3f,
                                CivilizationScheduleService.NpcAction.SLEEP, "打烊了，明天再来。"))));
        grabThrow.register(new GrabThrowService.GrabbableEntity(
                "barrel-valley-1", 228f, 0f, 188f, 3, 2.5f, true));
        massMomentum.registerMass(9001L, 5);
        massMomentum.registerMass(9002L, 9);
    }
}
