package beehub;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class EventDAO {

    // 내가 신청한 'ACTIVITY' 행사 목록 가져오기
    public List<MyActivityDTO> getMyActivityList(String hakbun) {
        List<MyActivityDTO> list = new ArrayList<>();
        
        // Activity(활동)이면서, 신청(APPLY) 상태인 것만 조회
        String sql = "SELECT e.event_name, e.event_date, e.location " +
                     "FROM event_participation ep " +
                     "JOIN events e ON ep.event_id = e.event_id " +
                     "WHERE ep.participant_hakbun = ? " +
                     "AND e.event_type = 'ACTIVITY' " +  
                     "AND ep.participation_type = 'APPLY' " +
                     "ORDER BY e.event_date DESC"; // 최신순 정렬

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, hakbun);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                
                while (rs.next()) {
                    // 날짜를 예쁘게 문자열로 변환
                    String dateStr = "-";
                    try {
                        dateStr = sdf.format(rs.getTimestamp("event_date"));
                    } catch (Exception e) { }

                    list.add(new MyActivityDTO(
                        rs.getString("event_name"),
                        dateStr,
                        rs.getString("location")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}