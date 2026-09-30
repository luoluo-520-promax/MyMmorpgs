package cn.itcast.demo.mymmorpg.reaction;

import cn.itcast.demo.mymmorpg.world.battle.ReactionValidator;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * battle-service 门面：瞬时博弈（完美闪避 / 弹反）服务端校验。
 */
@Service
public class BattleReactionService {

    private final ReactionValidator validator;

    public BattleReactionService(ReactionValidator validator) {
        this.validator = validator == null ? new ReactionValidator() : validator;
    }

    public BattleReactionService() {
        this(new ReactionValidator());
    }

    public Map<String, Object> openWindow(String attackId, long attackerEntityId, long nowMs) {
        return validator.openAttackWindow(attackId, attackerEntityId, nowMs,
                ReactionValidator.DEFAULT_DODGE_WINDOW_MS,
                ReactionValidator.DEFAULT_PARRY_WINDOW_MS);
    }

    public Map<String, Object> perfectDodge(
            String battleId, long playerId, String attackId, long clientTs, long nowMs) {
        return validator.validate(
                ReactionValidator.ReactionKind.PERFECT_DODGE,
                battleId, playerId, attackId, clientTs, nowMs);
    }

    public Map<String, Object> parry(
            String battleId, long playerId, String attackId, long clientTs, long nowMs) {
        return validator.validate(
                ReactionValidator.ReactionKind.PARRY,
                battleId, playerId, attackId, clientTs, nowMs);
    }

    public ReactionValidator validator() {
        return validator;
    }
}
