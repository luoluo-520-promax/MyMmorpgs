package cn.itcast.demo.mymmorpg.service.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 投诉本地规则分类与话术建议（无模型时可用）。
 */
public final class ComplaintClassifier {

    public record Advice(
            String category,
            String priority,
            String suggestedScript,
            List<String> relatedChecks,
            List<String> suggestedActions,
            List<String> warnings) {
    }

    private ComplaintClassifier() {
    }

    public static Advice classify(String content) {
        String text = content == null ? "" : content.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        String category;
        String priority = "P2";
        List<String> checks = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String script;

        if (containsAny(lower, "充值", "到账", "支付", "订单", "钻石没到", "pay", "recharge")) {
            category = "PAYMENT";
            priority = "P0";
            checks.add("只读核对玩家订单/充值流水（勿向公网模型发送完整支付凭证）");
            checks.add("确认渠道回调是否成功、订单号是否重复");
            actions.add("若核实未到账：生成补发工单待审批，禁止直接改库");
            script = "您好，已收到您关于充值未到账的反馈。请提供订单号/大致充值时间，我们将在核实渠道回调后为您处理。"
                    + "补发需审批，预计 1 个工作日内回复。";
        } else if (containsAny(lower, "掉线", "卡顿", "延迟", "进不去", "闪退", "断线", "lag")) {
            category = "TECHNICAL";
            priority = "P1";
            checks.add("查询玩家最近登录/战斗失败日志时段");
            checks.add("确认是否集中在某场景/匹配服");
            actions.add("引导重登与清理缓存；必要时收集设备与网络信息");
            script = "您好，抱歉影响了您的游戏体验。请尝试重新登录；若仍出现，请告知发生时间、所在玩法与设备型号，我们会继续排查。";
        } else if (containsAny(lower, "骂", "辱骂", "骚扰", "挂", "外挂", "作弊", "开挂")) {
            category = "ABUSE";
            priority = "P0";
            checks.add("检索相关聊天记录与举报（字段脱敏）");
            actions.add("建议禁言/封禁申请卡片，需人工二次确认");
            warnings.add("高危操作：不得由 AI 直接执行禁言或封号");
            script = "您好，我们已记录您的举报。请补充对方角色名/大致时间，运营将核实后按规则处理，处理结果将通过邮件或公告反馈。";
        } else if (containsAny(lower, "账号", "被盗", "密码", "找回", "封号", "解封")) {
            category = "ACCOUNT";
            priority = "P0";
            checks.add("核验账号绑定信息（脱敏）；查看近期异常登录");
            actions.add("引导官方找回流程；高危操作双人审批");
            script = "您好，账号类问题请通过官方找回渠道提交材料。为保障安全，我们不会在对话中索取完整密码；核实后将协助处理。";
        } else if (containsAny(lower, "活动", "奖励", "没领到", "签到", "邮件")) {
            category = "ACTIVITY_REWARD";
            priority = "P1";
            checks.add("只读查询活动进度与领奖记录");
            actions.add("若配置错误：走活动导入修正；若应补发：生成待审批发奖工单");
            script = "您好，已收到活动奖励相关反馈。请提供活动名称与领取时间，我们会核对进度后回复；如需补发将进入审批流程。";
        } else {
            category = "GENERAL";
            priority = "P2";
            checks.add("人工阅读原文并补充标签");
            actions.add("必要时转交对应业务组");
            script = "您好，已收到您的反馈，我们会尽快核查并回复。如有订单号、角色名或截图（注意脱敏）请一并提供。";
            warnings.add("未能自动细分类型，请人工复核");
        }

        if (text.isBlank()) {
            warnings.add("投诉内容为空");
            script = "投诉内容为空，请补充玩家描述后再生成话术。";
        }
        return new Advice(category, priority, script, checks, actions, warnings);
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
