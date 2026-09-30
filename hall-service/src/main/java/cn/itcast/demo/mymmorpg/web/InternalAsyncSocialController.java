package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.CoopRoomService;
import cn.itcast.demo.mymmorpg.service.CrossServerFriendService;
import cn.itcast.demo.mymmorpg.service.FriendAssistService;
import cn.itcast.demo.mymmorpg.service.HomeVisitService;
import cn.itcast.demo.mymmorpg.service.PartyManageService;
import cn.itcast.demo.mymmorpg.service.SocialEventPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 异步轻社交内部 API：嵌入 player-service 或独立 hall-service 均可注册。
 * 含联机房间（CoopRoom）、队伍管理、跨服好友、家园扩展与助战评价。
 */
@RestController
@RequestMapping("/internal/hall")
public class InternalAsyncSocialController {

    private final FriendAssistService friendAssistService;
    private final HomeVisitService homeVisitService;
    private final CoopRoomService coopRoomService;
    private final CrossServerFriendService crossServerFriendService;
    private final PartyManageService partyManageService;
    private final SocialEventPublisher socialEventPublisher;

    public InternalAsyncSocialController(FriendAssistService friendAssistService,
                                         HomeVisitService homeVisitService,
                                         CoopRoomService coopRoomService,
                                         CrossServerFriendService crossServerFriendService,
                                         PartyManageService partyManageService,
                                         SocialEventPublisher socialEventPublisher) {
        this.friendAssistService = friendAssistService;
        this.homeVisitService = homeVisitService;
        this.coopRoomService = coopRoomService;
        this.crossServerFriendService = crossServerFriendService;
        this.partyManageService = partyManageService;
        this.socialEventPublisher = socialEventPublisher;
    }

    @PostMapping("/assist/offer")
    public Map<String, Object> offerAssist(@RequestHeader("X-Player-Id") long playerId,
                                           @RequestParam(defaultValue = "0") int slot,
                                           @RequestBody(required = false) Map<String, Object> body) {
        return friendAssistService.offerAssist(playerId, slot, body);
    }

    @GetMapping("/assist/lineups")
    public Map<String, Object> assistLineups(@RequestParam long ownerId) {
        return friendAssistService.listLineups(ownerId);
    }

    @GetMapping("/assist/offers")
    public List<Map<String, Object>> assistOffers(@RequestHeader("X-Player-Id") long playerId) {
        return friendAssistService.listOffers(playerId);
    }

    @PostMapping("/assist/borrow")
    public Map<String, Object> borrowAssist(@RequestHeader("X-Player-Id") long playerId,
                                            @RequestParam long ownerId,
                                            @RequestParam(defaultValue = "-1") int slot) {
        return friendAssistService.borrow(playerId, ownerId, slot);
    }

    @GetMapping("/assist/mine")
    public Map<String, Object> myAssist(@RequestHeader("X-Player-Id") long playerId) {
        return friendAssistService.myBorrowed(playerId);
    }

    @PostMapping("/assist/revoke")
    public Map<String, Object> revokeAssist(@RequestHeader("X-Player-Id") long playerId) {
        return friendAssistService.revoke(playerId);
    }

    @PostMapping("/assist/settle")
    public Map<String, Object> settleAssist(@RequestHeader("X-Player-Id") long playerId,
                                            @RequestParam(defaultValue = "true") boolean victory) {
        return friendAssistService.settle(playerId, victory);
    }

    @PostMapping("/assist/thank")
    public Map<String, Object> thankAssist(@RequestHeader("X-Player-Id") long playerId,
                                           @RequestParam long ownerId,
                                           @RequestParam(defaultValue = "5") int rating,
                                           @RequestParam(required = false) String message) {
        return friendAssistService.thank(playerId, ownerId, rating, message);
    }

    @PostMapping("/assist/claim-reward")
    public Map<String, Object> claimAssistReward(@RequestHeader("X-Player-Id") long playerId) {
        return friendAssistService.claimReward(playerId);
    }

    @GetMapping("/assist/stats")
    public Map<String, Object> assistStats(@RequestHeader("X-Player-Id") long playerId) {
        return friendAssistService.stats(playerId);
    }

    @PostMapping("/home/publish")
    public Map<String, Object> publishHome(@RequestHeader("X-Player-Id") long playerId,
                                           @RequestParam(required = false) String name,
                                           @RequestParam(required = false) String accessPolicy,
                                           @RequestBody(required = false) String layoutJson) {
        return homeVisitService.publishHome(playerId, name, layoutJson, accessPolicy);
    }

    @PostMapping("/home/access")
    public Map<String, Object> setHomeAccess(@RequestHeader("X-Player-Id") long playerId,
                                             @RequestParam String accessPolicy) {
        return homeVisitService.setAccess(playerId, accessPolicy);
    }

    @PostMapping("/home/decorations")
    public Map<String, Object> syncDecorations(@RequestHeader("X-Player-Id") long playerId,
                                               @RequestBody(required = false) List<Map<String, Object>> decorations) {
        return homeVisitService.syncDecorations(playerId, decorations);
    }

    @GetMapping("/home/decorations")
    public Map<String, Object> getDecorations(@RequestParam long ownerId) {
        return homeVisitService.getDecorations(ownerId);
    }

    @GetMapping("/home")
    public Map<String, Object> getHome(@RequestParam long ownerId) {
        return homeVisitService.getHome(ownerId);
    }

    @PostMapping("/home/visit")
    public Map<String, Object> visitHome(@RequestHeader("X-Player-Id") long playerId,
                                         @RequestParam long ownerId) {
        return homeVisitService.visit(playerId, ownerId);
    }

    @PostMapping("/home/minigame")
    public Map<String, Object> homeMiniGame(@RequestHeader("X-Player-Id") long playerId,
                                            @RequestParam long ownerId,
                                            @RequestParam String furnitureId,
                                            @RequestParam(defaultValue = "0") int score) {
        return homeVisitService.playMiniGame(playerId, ownerId, furnitureId, score);
    }

    @PostMapping("/home/rate")
    public Map<String, Object> rateHome(@RequestHeader("X-Player-Id") long playerId,
                                        @RequestParam long ownerId,
                                        @RequestParam int score) {
        return homeVisitService.rateHome(playerId, ownerId, score);
    }

    @GetMapping("/home/ranking")
    public Map<String, Object> homeRanking(@RequestParam(defaultValue = "20") int limit) {
        return homeVisitService.ranking(limit);
    }

    @GetMapping("/home/shop")
    public Map<String, Object> homeShop() {
        return homeVisitService.shopCatalog();
    }

    @PostMapping("/home/shop/buy")
    public Map<String, Object> buyHomeItem(@RequestHeader("X-Player-Id") long playerId,
                                           @RequestParam String itemId) {
        return homeVisitService.buyShopItem(playerId, itemId);
    }

    @GetMapping("/home/wallet")
    public Map<String, Object> homeWallet(@RequestHeader("X-Player-Id") long playerId) {
        return homeVisitService.wallet(playerId);
    }

    @GetMapping("/home/friends")
    public List<Map<String, Object>> friendHomes(@RequestHeader("X-Player-Id") long playerId) {
        return homeVisitService.listFriendHomes(playerId);
    }

    @PostMapping("/coop/create")
    public Map<String, Object> createCoop(@RequestHeader("X-Player-Id") long playerId,
                                          @RequestParam(required = false) String bossEventId,
                                          @RequestParam(defaultValue = "1000000") long bossHpMax) {
        return coopRoomService.toView(coopRoomService.create(playerId, bossEventId, bossHpMax));
    }

    @PostMapping("/coop/join")
    public Map<String, Object> joinCoop(@RequestHeader("X-Player-Id") long playerId,
                                        @RequestParam String roomId) {
        return coopRoomService.toView(coopRoomService.join(playerId, roomId));
    }

    @PostMapping("/coop/leave")
    public Map<String, Object> leaveCoop(@RequestHeader("X-Player-Id") long playerId) {
        return coopRoomService.toView(coopRoomService.leave(playerId));
    }

    @PostMapping("/coop/start-boss")
    public Map<String, Object> startBoss(@RequestHeader("X-Player-Id") long playerId,
                                         @RequestParam String roomId) {
        return coopRoomService.toView(coopRoomService.startBoss(roomId, playerId));
    }

    @PostMapping("/coop/damage")
    public Map<String, Object> coopDamage(@RequestHeader("X-Player-Id") long playerId,
                                          @RequestParam String roomId,
                                          @RequestParam long damage) {
        return coopRoomService.toView(coopRoomService.reportDamage(roomId, playerId, damage));
    }

    @GetMapping("/coop/mine")
    public Map<String, Object> myCoop(@RequestHeader("X-Player-Id") long playerId) {
        return coopRoomService.toView(coopRoomService.currentOf(playerId));
    }

    @PostMapping("/coop/emote")
    public Map<String, Object> coopEmote(@RequestHeader("X-Player-Id") long playerId,
                                         @RequestParam String roomId,
                                         @RequestParam(defaultValue = "wave") String emoteCode) {
        return coopRoomService.sendEmote(roomId, playerId, emoteCode);
    }

    @PostMapping("/coop/action")
    public Map<String, Object> coopAction(@RequestHeader("X-Player-Id") long playerId,
                                          @RequestParam String roomId,
                                          @RequestParam(defaultValue = "cheer") String actionCode) {
        return coopRoomService.sendAction(roomId, playerId, actionCode);
    }

    @PostMapping("/coop/phrase")
    public Map<String, Object> coopPhrase(@RequestHeader("X-Player-Id") long playerId,
                                          @RequestParam String roomId,
                                          @RequestParam(defaultValue = "nice") String phraseId) {
        return coopRoomService.sendQuickPhrase(roomId, playerId, phraseId);
    }

    @PostMapping("/coop/spectate")
    public Map<String, Object> coopSpectate(@RequestHeader("X-Player-Id") long playerId,
                                            @RequestParam String roomId) {
        return coopRoomService.spectate(playerId, roomId);
    }

    @PostMapping("/coop/spectate/leave")
    public Map<String, Object> leaveSpectate(@RequestHeader("X-Player-Id") long playerId) {
        return coopRoomService.leaveSpectate(playerId);
    }

    @PostMapping("/coop/like")
    public Map<String, Object> coopLike(@RequestHeader("X-Player-Id") long playerId,
                                        @RequestParam String roomId,
                                        @RequestParam long toId) {
        return coopRoomService.like(roomId, playerId, toId);
    }

    @PostMapping("/coop/flower")
    public Map<String, Object> coopFlower(@RequestHeader("X-Player-Id") long playerId,
                                          @RequestParam String roomId,
                                          @RequestParam long toId,
                                          @RequestParam(defaultValue = "1") int count) {
        return coopRoomService.sendFlower(roomId, playerId, toId, count);
    }

    @PostMapping("/cross-friends/add")
    public Map<String, Object> addCrossFriend(@RequestHeader("X-Player-Id") long playerId,
                                              @RequestParam long friendId) {
        return crossServerFriendService.addFriend(playerId, friendId);
    }

    @PostMapping("/cross-friends/remove")
    public Map<String, Object> removeCrossFriend(@RequestHeader("X-Player-Id") long playerId,
                                                 @RequestParam long friendId) {
        return crossServerFriendService.removeFriend(playerId, friendId);
    }

    @GetMapping("/cross-friends")
    public Map<String, Object> listCrossFriends(@RequestHeader("X-Player-Id") long playerId) {
        return crossServerFriendService.toView(playerId);
    }

    @PostMapping("/party/create")
    public Map<String, Object> createParty(@RequestHeader("X-Player-Id") long playerId) {
        Map<String, Object> view = partyManageService.create(playerId);
        if (Boolean.TRUE.equals(view.get("ok"))) {
            socialEventPublisher.publishPartyFormed(playerId, String.valueOf(view.get("partyId")), 1);
        }
        return view;
    }

    @PostMapping("/party/join")
    public Map<String, Object> joinParty(@RequestHeader("X-Player-Id") long playerId,
                                         @RequestParam String inviteCode) {
        Map<String, Object> view = partyManageService.joinByInvite(playerId, inviteCode);
        if (Boolean.TRUE.equals(view.get("ok"))) {
            int count = view.get("memberCount") instanceof Number n ? n.intValue() : 0;
            socialEventPublisher.publishPartyFormed(
                    ((Number) view.getOrDefault("leaderId", playerId)).longValue(),
                    String.valueOf(view.get("partyId")),
                    count);
        }
        return view;
    }

    @PostMapping("/party/kick")
    public Map<String, Object> kickParty(@RequestHeader("X-Player-Id") long playerId,
                                         @RequestParam long targetId) {
        return partyManageService.kick(playerId, targetId);
    }

    @PostMapping("/party/transfer")
    public Map<String, Object> transferParty(@RequestHeader("X-Player-Id") long playerId,
                                             @RequestParam long newLeaderId) {
        return partyManageService.transferLeader(playerId, newLeaderId);
    }

    @PostMapping("/party/leave")
    public Map<String, Object> leaveParty(@RequestHeader("X-Player-Id") long playerId) {
        return partyManageService.leave(playerId);
    }

    @GetMapping("/party/mine")
    public Map<String, Object> myParty(@RequestHeader("X-Player-Id") long playerId) {
        return partyManageService.mine(playerId);
    }
}
