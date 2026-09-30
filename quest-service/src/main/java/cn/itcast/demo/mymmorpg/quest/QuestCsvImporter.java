package cn.itcast.demo.mymmorpg.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV：questId,name,description,questType,target,expReward,goldReward,completeOnAccept
 */
public final class QuestCsvImporter {

    private QuestCsvImporter() {
    }

    public static List<QuestTemplate> parse(String csv) {
        List<QuestTemplate> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        String[] lines = csv.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        boolean headerSkipped = false;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (!headerSkipped && line.toLowerCase().contains("questid")) {
                headerSkipped = true;
                continue;
            }
            headerSkipped = true;
            String[] cols = line.split(",", -1);
            if (cols.length < 7) {
                continue;
            }
            QuestTemplate q = new QuestTemplate();
            q.questId = parseInt(cols[0], 0);
            q.name = cols[1].trim();
            q.description = cols[2].trim();
            q.questType = parseInt(cols[3], 0);
            q.target = parseInt(cols[4], 1);
            q.expReward = parseInt(cols[5], 0);
            q.goldReward = parseInt(cols[6], 0);
            q.completeOnAccept = cols.length > 7 && parseBool(cols[7]);
            out.add(q);
        }
        return out;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean parseBool(String s) {
        String v = s == null ? "" : s.trim().toLowerCase();
        return "1".equals(v) || "true".equals(v) || "yes".equals(v);
    }
}
