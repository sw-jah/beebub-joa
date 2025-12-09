package admin;

import beehub.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LotteryManager {

    // 한 번 응모할 때 기본 차감 꿀
    public static final int DEFAULT_COST_POINTS = 100;

    // 🔹 MyPageFrame에서 부르는 메소드
    public static boolean applyUsingPoints(int roundId, String hakbun) {
        return applyUsingPoints(roundId, hakbun, DEFAULT_COST_POINTS);
    }

    // ===================== DTO =====================

    public static class LotteryRound {
        public int roundId;
        public String name;               // 회차 이름
        public String prizeName;          // 경품 이름
        public int winnerCount;           // 당첨 인원 수
        public String announcementDate;   // 발표일
        public String applicationPeriod;  // 응모기간 (yyyy-MM-dd HH:mm:ss)
        public String pickupLocation;     // 수령 장소
        public String pickupPeriod;       // 수령 기간
        public boolean isDrawn;           // 추첨 완료 여부
        public List<Applicant> applicants = new ArrayList<>();  // 응모자 목록

        public void addApplicant(String name, String hakbun, int count) {
            Applicant a = new Applicant();
            a.name = name;
            a.hakbun = hakbun;
            a.count = count;
            a.status = "대기";
            applicants.add(a);
        }
    }

    public static class Applicant {
        public String name;     // 응모자 이름
        public String hakbun;   // 학번
        public int count;       // 응모 횟수
        public String status;   // "대기", "당첨", "미당첨"
    }

    // ===================== 유틸 =====================

    private static String stripRoundPrefix(String rawName) {
        if (rawName == null) return "";
        int idx = rawName.indexOf(":");
        if (idx > 0 && rawName.substring(0, idx).contains("회차")) {
            return rawName.substring(idx + 1).trim();
        }
        return rawName;
    }

    // ===================== 회차 전체 조회 =====================

    public static List<LotteryRound> getAllRounds() {
        List<LotteryRound> list = new ArrayList<>();

        String sql =
                "SELECT round_id, round_name, prize_name, winner_count, " +
                "       announcement_date, application_start, application_end, " +
                "       pickup_location, pickup_start, pickup_end, is_drawn " +
                "FROM lottery_round " +
                "ORDER BY round_id ASC";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            // 날짜 포맷터 (시간 포함)
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            while (rs.next()) {
                LotteryRound r = new LotteryRound();
                r.roundId = rs.getInt("round_id");

                String rawName = rs.getString("round_name");
                r.name = stripRoundPrefix(rawName);

                r.prizeName   = rs.getString("prize_name");
                r.winnerCount = rs.getInt("winner_count");

                // java.sql.Date 명시
                java.sql.Date annDate = rs.getDate("announcement_date");
                r.announcementDate = (annDate != null) ? annDate.toString() : "";

                // java.sql.Timestamp 명시
                java.sql.Timestamp appStart = rs.getTimestamp("application_start");
                java.sql.Timestamp appEnd   = rs.getTimestamp("application_end");
                
                // 시간 정보를 포함해서 문자열로 저장
                if (appStart != null && appEnd != null) {
                    r.applicationPeriod =
                            appStart.toLocalDateTime().format(dtf) + " ~ " +
                            appEnd.toLocalDateTime().format(dtf);
                } else {
                    r.applicationPeriod = "-";
                }

                r.pickupLocation = rs.getString("pickup_location");

                java.sql.Timestamp pickStart = rs.getTimestamp("pickup_start");
                java.sql.Timestamp pickEnd   = rs.getTimestamp("pickup_end");
                
                if (pickStart != null && pickEnd != null) {
                    r.pickupPeriod =
                            pickStart.toLocalDateTime().format(dtf) + " ~ " +
                            pickEnd.toLocalDateTime().format(dtf);
                } else {
                    r.pickupPeriod = "-";
                }

                r.isDrawn = rs.getInt("is_drawn") == 1;
                r.applicants = getApplicantsByRound(r.roundId);

                if (!r.isDrawn) {
                    for (Applicant a : r.applicants) {
                        a.status = "대기";
                    }
                }

                list.add(r);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    // ===================== 한 회차 응모자 조회 =====================

    public static List<Applicant> getApplicantsByRound(int roundId) {
        List<Applicant> list = new ArrayList<>();

        String sql =
                "SELECT e.hakbun, m.name, e.entry_count, e.is_win " +
                "FROM lottery_entry e " +
                "JOIN members m ON e.hakbun = m.hakbun " +
                "WHERE e.round_id = ? " +
                "ORDER BY e.raffle_id ASC";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, roundId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Applicant a = new Applicant();
                    a.hakbun = rs.getString("hakbun");
                    a.name   = rs.getString("name");
                    a.count  = rs.getInt("entry_count");

                    String winRaw = rs.getString("is_win");

                    if (winRaw == null) {
                        a.status = "미당첨";
                    } else {
                        winRaw = winRaw.trim();
                        if ("W".equalsIgnoreCase(winRaw) || "1".equals(winRaw)) {
                            a.status = "당첨";
                        } else {
                            a.status = "미당첨";
                        }
                    }
                    list.add(a);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    // ===================== 응모 (포인트 사용) =====================

    public static boolean applyUsingPoints(int roundId, String hakbun, int costPoints) {

        String selectPointSql = "SELECT point FROM members WHERE hakbun = ?";
        String updatePointSql = "UPDATE members SET point = point - ? WHERE hakbun = ?";
        String selectEntrySql = "SELECT entry_count FROM lottery_entry WHERE round_id = ? AND hakbun = ?";
        String insertEntrySql = "INSERT INTO lottery_entry (round_id, hakbun, entry_count, is_win) VALUES (?, ?, 1, 0)";
        String updateEntrySql = "UPDATE lottery_entry SET entry_count = entry_count + 1 WHERE round_id = ? AND hakbun = ?";
        String selectRoundPeriodSql = "SELECT application_start, application_end FROM lottery_round WHERE round_id = ?";

        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);

            // 기간 체크
            try (PreparedStatement ps = conn.prepareStatement(selectRoundPeriodSql)) {
                ps.setInt(1, roundId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        java.sql.Timestamp tsStart = rs.getTimestamp("application_start");
                        java.sql.Timestamp tsEnd   = rs.getTimestamp("application_end");

                        if (tsStart != null && tsEnd != null) {
                            LocalDateTime now = LocalDateTime.now();
                            LocalDateTime start = tsStart.toLocalDateTime();
                            LocalDateTime end   = tsEnd.toLocalDateTime();

                            if (now.isBefore(start) || now.isAfter(end)) {
                                System.out.println("[Lottery] 응모 기간이 아님.");
                                conn.rollback();
                                return false;
                            }
                        }
                    }
                }
            }

            int currentPoint;
            try (PreparedStatement pstmt = conn.prepareStatement(selectPointSql)) {
                pstmt.setString(1, hakbun);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }
                    currentPoint = rs.getInt("point");
                }
            }

            if (currentPoint < costPoints) {
                conn.rollback();
                return false;
            }

            try (PreparedStatement pstmt = conn.prepareStatement(updatePointSql)) {
                pstmt.setInt(1, costPoints);
                pstmt.setString(2, hakbun);
                pstmt.executeUpdate();
            }

            boolean exists;
            try (PreparedStatement pstmt = conn.prepareStatement(selectEntrySql)) {
                pstmt.setInt(1, roundId);
                pstmt.setString(2, hakbun);
                try (ResultSet rs = pstmt.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (exists) {
                try (PreparedStatement pstmt = conn.prepareStatement(updateEntrySql)) {
                    pstmt.setInt(1, roundId);
                    pstmt.setString(2, hakbun);
                    pstmt.executeUpdate();
                }
            } else {
                try (PreparedStatement pstmt = conn.prepareStatement(insertEntrySql)) {
                    pstmt.setInt(1, roundId);
                    pstmt.setString(2, hakbun);
                    pstmt.executeUpdate();
                }
            }

            conn.commit();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ===================== 회차 추가 =====================

    public static boolean addRound(String titleOnly, String prize, int count,
                                   String annDateStr,
                                   String appStartStr, String appEndStr,
                                   String loc,
                                   String pickStartStr, String pickEndStr) {

        String sql =
                "INSERT INTO lottery_round " +
                "(round_name, prize_name, winner_count, " +
                " announcement_date, application_start, application_end, " +
                " pickup_location, pickup_start, pickup_end, is_drawn) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, titleOnly);
            pstmt.setString(2, prize);
            pstmt.setInt(3, count);

            // 발표일
            LocalDate ann = LocalDate.parse(annDateStr);
            pstmt.setDate(4, java.sql.Date.valueOf(ann)); // java.sql.Date 명시

            // 기간 (시:분:초 포함)
            DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]");

            LocalDateTime appStart  = LocalDateTime.parse(appStartStr, dtFmt);
            LocalDateTime appEnd    = LocalDateTime.parse(appEndStr, dtFmt);
            LocalDateTime pickStart = LocalDateTime.parse(pickStartStr, dtFmt);
            LocalDateTime pickEnd   = LocalDateTime.parse(pickEndStr, dtFmt);

            pstmt.setTimestamp(5, java.sql.Timestamp.valueOf(appStart));
            pstmt.setTimestamp(6, java.sql.Timestamp.valueOf(appEnd));
            pstmt.setString(7, loc);
            pstmt.setTimestamp(8, java.sql.Timestamp.valueOf(pickStart));
            pstmt.setTimestamp(9, java.sql.Timestamp.valueOf(pickEnd));

            return pstmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ===================== 추첨 결과 저장 =====================

    public static boolean saveDrawResult(LotteryRound round) {
        String sqlUpdateRound = "UPDATE lottery_round SET is_drawn = 1 WHERE round_id = ?";
        String sqlUpdateApplicant = "UPDATE lottery_entry SET is_win = ? WHERE round_id = ? AND hakbun = ?";

        Connection conn = null;
        PreparedStatement psRound = null;
        PreparedStatement psApp = null;

        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            psRound = conn.prepareStatement(sqlUpdateRound);
            psRound.setInt(1, round.roundId);
            psRound.executeUpdate();

            psApp = conn.prepareStatement(sqlUpdateApplicant);
            for (Applicant a : round.applicants) {
                int isWinValue = "당첨".equals(a.status) ? 1 : 0;
                psApp.setInt(1, isWinValue);
                psApp.setInt(2, round.roundId);
                psApp.setString(3, a.hakbun);
                psApp.addBatch();
            }
            psApp.executeBatch();

            conn.commit();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            if (conn != null) {
                try { conn.rollback(); } catch (Exception ignore) {}
            }
            return false;
        } finally {
            try { if (psApp != null) psApp.close(); } catch (Exception ignored) {}
            try { if (psRound != null) psRound.close(); } catch (Exception ignored) {}
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }
}