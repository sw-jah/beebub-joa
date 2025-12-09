package council;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import council.EventManager.EventData;
import council.EventManager.FeeType;

public class CouncilEventAddDialog extends JDialog {

    private static final Color BG_WHITE = new Color(255, 255, 255);
    private static final Color BROWN    = new Color(139, 90, 43);
    // [추가] 팝업 배경색 상수 (다른 프레임과 통일)
    private static final Color POPUP_BG = new Color(255, 250, 205); 


    private static Font uiFont;
    static {
        try {
            InputStream is = CouncilEventAddDialog.class.getResourceAsStream("/fonts/DNFBitBitv2.ttf");
            if (is == null) uiFont = new Font("맑은 고딕", Font.PLAIN, 12);
            else uiFont = Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(12f);
        } catch (Exception e) {
            uiFont = new Font("맑은 고딕", Font.PLAIN, 12);
        }
    }

    // ==============================
    //  필드
    // ==============================

    /** 수정 or 추가 대상 EventData (수정일 때는 기존 객체) */
    private EventData eventData;

    /** 저장 후 리스트 리프레시용 콜백 */
    private Runnable onSavedCallback;

    // 입력 컴포넌트
    private JTextField titleField;
    private JTextField locationField;
    private JTextField eventDateField;     // yyyy-MM-dd HH:mm
    private JTextField applyStartField;    // yyyy-MM-dd HH:mm
    private JTextField applyEndField;      // yyyy-MM-dd HH:mm
    private JTextField totalCountField;
    private JTextField targetDeptField;
    private JTextField secretCodeField;
    private JTextArea  descriptionArea;
    private JComboBox<String> typeCombo;   // SNACK / ACTIVITY
    private JComboBox<String> feeCombo;    // 회비 조건

    private final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ==============================
    //  생성자들
    // ==============================

    /** ✅ 새 행사 추가용 생성자
     * CouncilMainFrame 에서:
     * new CouncilEventAddDialog(this, councilId, this::refreshLists);
     */
    public CouncilEventAddDialog(Frame owner, String ownerHakbun, Runnable onSavedCallback) {
        this(owner, (EventData) null, onSavedCallback);
        // ownerHakbun은 새 행사일 때만 세팅
        this.eventData.ownerHakbun = ownerHakbun;
    }

    /** ✅ 공용 생성자 (추가 / 수정 겸용) */
    public CouncilEventAddDialog(Frame owner, EventData existing, Runnable onSavedCallback) {
        super(owner, true);
        this.onSavedCallback = onSavedCallback;

        if (existing == null) {
            // 새 행사
            this.eventData = new EventData();
            this.eventData.eventId      = 0;          // 0 → INSERT
            this.eventData.totalCount   = 0;
            this.eventData.currentCount = 0;
            this.eventData.status       = "진행중";    // 기본값
            this.eventData.eventType    = "ACTIVITY"; // 기본 과행사
            this.eventData.requiredFee  = FeeType.NONE;
        } else {
            // 수정용
            this.eventData = existing;
        }

        initUI();
        fillFormFromEventData();
    }

    // ==============================
    //  UI 구성
    // ==============================

    private void initUI() {
        setTitle(eventData.eventId == 0 ? "행사 등록" : "행사 수정");
        setSize(550, 650);
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel();
        content.setBackground(BG_WHITE);
        content.setBorder(new EmptyBorder(15, 15, 15, 15));
        content.setLayout(new BorderLayout(10, 10));
        setContentPane(content);

        JLabel titleLabel = new JLabel(eventData.eventId == 0 ? "새 행사 등록" : "행사 정보 수정");
        titleLabel.setFont(uiFont.deriveFont(Font.BOLD, 20f));
        titleLabel.setForeground(BROWN);
        content.add(titleLabel, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new GridBagLayout());
        content.add(new JScrollPane(form), BorderLayout.CENTER);

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill   = GridBagConstraints.HORIZONTAL;
        gc.gridx  = 0;
        gc.gridy  = 0;
        gc.weightx = 0;

        java.util.function.BiConsumer<String, JComponent> addRow = (labelText, comp) -> {
            JLabel lab = new JLabel(labelText);
            lab.setFont(uiFont.deriveFont(Font.BOLD, 13f));
            lab.setForeground(BROWN);

            gc.gridx = 0;
            gc.weightx = 0;
            form.add(lab, gc);

            gc.gridx = 1;
            gc.weightx = 1;
            form.add(comp, gc);
            gc.gridy++;
        };

        titleField      = new JTextField();
        locationField   = new JTextField();
        eventDateField  = new JTextField("2025-12-09 12:00");
        applyStartField = new JTextField("2025-12-08 12:00");
        applyEndField   = new JTextField("2025-12-08 15:00");
        totalCountField = new JTextField();
        targetDeptField = new JTextField();
        secretCodeField = new JTextField();

        descriptionArea = new JTextArea(5, 20);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        typeCombo = new JComboBox<>(new String[]{
                "ACTIVITY (과행사)",
                "SNACK (간식 배부)"
        });

        feeCombo = new JComboBox<>(new String[]{
                FeeType.NONE.getLabel(),
                FeeType.SCHOOL.getLabel(),
                FeeType.DEPT.getLabel()
        });

        addRow.accept("행사명",           titleField);
        addRow.accept("장소",             locationField);
        addRow.accept("행사 일시",        eventDateField);
        addRow.accept("신청 시작",        applyStartField);
        addRow.accept("신청 종료",        applyEndField);
        addRow.accept("정원",             totalCountField);
        addRow.accept("대상 학과 / 전체", targetDeptField);
        addRow.accept("비밀코드 (출석 등)", secretCodeField);
        addRow.accept("행사 타입",        typeCombo);
        addRow.accept("회비 조건",        feeCombo);

        gc.gridx = 0;
        gc.gridwidth = 2;
        gc.weightx = 1;
        JLabel descLabel = new JLabel("상세 설명");
        descLabel.setFont(uiFont.deriveFont(Font.BOLD, 13f));
        descLabel.setForeground(BROWN);
        form.add(descLabel, gc);
        gc.gridy++;

        JScrollPane descScroll = new JScrollPane(descriptionArea);
        descScroll.setPreferredSize(new Dimension(400, 120));
        form.add(descScroll, gc);
        gc.gridy++;

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setOpaque(false);

        JButton saveBtn   = new JButton(eventData.eventId == 0 ? "등록" : "수정 완료");
        JButton cancelBtn = new JButton("취소");

        saveBtn.setFont(uiFont.deriveFont(Font.BOLD, 14f));
        saveBtn.setBackground(BROWN);
        saveBtn.setForeground(Color.WHITE);

        cancelBtn.setFont(uiFont.deriveFont(14f));

        saveBtn.addActionListener(this::onSave);
        cancelBtn.addActionListener(ev -> dispose());

        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        content.add(btnPanel, BorderLayout.SOUTH);
    }

    /** 기존 EventData 내용 → 폼에 채우기 (수정 모드일 때) */
    private void fillFormFromEventData() {
        if (eventData == null) return;

        if (eventData.title != null)       titleField.setText(eventData.title);
        if (eventData.location != null)    locationField.setText(eventData.location);
        if (eventData.date != null)        eventDateField.setText(eventData.date.format(FMT));
        if (eventData.applyStart != null)  applyStartField.setText(eventData.applyStart.format(FMT));
        if (eventData.applyEnd != null)    applyEndField.setText(eventData.applyEnd.format(FMT));
        if (eventData.totalCount > 0)      totalCountField.setText(String.valueOf(eventData.totalCount));
        if (eventData.targetDept != null)  targetDeptField.setText(eventData.targetDept);
        if (eventData.secretCode != null)  secretCodeField.setText(eventData.secretCode);
        if (eventData.description != null) descriptionArea.setText(eventData.description);

        String type = (eventData.eventType != null) ? eventData.eventType.toUpperCase() : "ACTIVITY";
        if (type.startsWith("SNACK")) typeCombo.setSelectedIndex(1);
        else                          typeCombo.setSelectedIndex(0);

        if (eventData.requiredFee != null) {
            switch (eventData.requiredFee) {
                case SCHOOL: feeCombo.setSelectedIndex(1); break;
                case DEPT:   feeCombo.setSelectedIndex(2); break;
                case NONE:
                default:     feeCombo.setSelectedIndex(0); break;
            }
        } else {
            feeCombo.setSelectedIndex(0);
        }
    }

    // ==============================
    //  저장 버튼 로직
    // ==============================

    private void onSave(ActionEvent ev) {
        try {
            // ⚠️ INSERT/UPDATE 여부는 addEvent() 호출 전에 따로 저장
            boolean isNew = (eventData.eventId == 0);

            // 필수값 체크
            String title = titleField.getText().trim();
            if (title.isEmpty()) {
                showCustomMsgPopup("입력 오류", "행사명을 입력하세요.");
                return;
            }

            // 날짜 파싱 및 오류 체크 (parseDateTime 수정에 따라 로직 변경)
            LocalDateTime eventDate  = parseDateTime(eventDateField.getText().trim(),  "행사 일시");
            if (eventDate == null) return; // 파싱 실패 시 showCustomMsgPopup이 이미 호출됨.

            LocalDateTime applyStart = parseDateTime(applyStartField.getText().trim(), "신청 시작");
            if (applyStart == null) return; // 파싱 실패 시 showCustomMsgPopup이 이미 호출됨.

            LocalDateTime applyEnd   = parseDateTime(applyEndField.getText().trim(),   "신청 종료");
            if (applyEnd == null) return; // 파싱 실패 시 showCustomMsgPopup이 이미 호출됨.


            // ================================================================
            // 🔥 현재 시간(현실)보다 이전인지 체크 (행사 일시, 신청 시작/종료)
            // ================================================================
            LocalDateTime now = LocalDateTime.now();
            String nowStr = now.format(FMT); // 메시지 출력을 위한 현재 시간 포맷

            // 1. 행사 일시 체크
            if (eventDate.isBefore(now)) {
                showCustomMsgPopup(
                        "날짜 오류", 
                        "행사 일시(" + eventDate.format(FMT) + ")는\n 현재 시각(" + nowStr + ")보다 이후여야 합니다."
                );
                return;
            }
            
            // 2. 신청 시작 일시 체크
            if (applyStart.isBefore(now)) {
                 showCustomMsgPopup(
                        "날짜 오류",
                        "신청 시작 일시(" + applyStart.format(FMT) + ")는\n 현재 시각(" + nowStr + ")보다 이후여야 합니다."
                 );
                 return;
            }
            
            // 3. 신청 마감 일시 체크
            if (applyEnd.isBefore(now)) {
                 showCustomMsgPopup(
                        "날짜 오류",
                        "신청 마감 일시(" + applyEnd.format(FMT) + ")는\n 현재 시각(" + nowStr + ")보다 이후여야 합니다."
                 );
                 return;
            }
            // ================================================================


            // [기존 로직] 신청 종료 시간이 시작 시간보다 빠를 수 없음 체크
            if (applyEnd.isBefore(applyStart)) {
                showCustomMsgPopup("날짜 오류", "신청 종료 시간이 신청 시작 시간보다 빠를 수 없습니다.");
                return;
            }

            int totalCount = 0;
            String totalStr = totalCountField.getText().trim();
            if (!totalStr.isEmpty()) {
                totalCount = Integer.parseInt(totalStr);
                if (totalCount < 0) totalCount = 0;
            }

            // ✅ eventData(기존 객체)에 덮어쓰기
            eventData.title        = title;
            eventData.location     = locationField.getText().trim();
            eventData.date         = eventDate;
            eventData.startDateTime = eventDate; // 호환 필드
            eventData.applyStart   = applyStart;
            eventData.applyEnd     = applyEnd;
            eventData.totalCount   = totalCount;
            eventData.targetDept   = targetDeptField.getText().trim();
            eventData.secretCode   = secretCodeField.getText().trim();
            eventData.description  = descriptionArea.getText();

            // 타입 설정
            int typeIdx = typeCombo.getSelectedIndex();
            if (typeIdx == 1) eventData.eventType = "SNACK";
            else              eventData.eventType = "ACTIVITY";

            // 회비 조건
            int feeIdx = feeCombo.getSelectedIndex();
            switch (feeIdx) {
                case 1: eventData.requiredFee = FeeType.SCHOOL; break;
                case 2: eventData.requiredFee = FeeType.DEPT;   break;
                default: eventData.requiredFee = FeeType.NONE;  break;
            }

            // 상태 기본값 (신규면 진행중)
            if (eventData.status == null || eventData.status.isEmpty()) {
                eventData.status = "진행중";
            }

            // 🔥 여기서 INSERT/UPDATE 실행
            EventManager.addEvent(eventData);

            showCustomMsgPopup(
                    "성공",
                    isNew ? "행사 등록이 완료되었습니다." : "행사 수정이 완료되었습니다."
            );

            if (onSavedCallback != null) {
                onSavedCallback.run();   // CouncilMainFrame.refreshLists()
            }

            dispose();

        } catch (NumberFormatException ex) {
            showCustomMsgPopup("입력 오류", "정원은 숫자로 입력해주세요.");
        } catch (Exception ex) {
            ex.printStackTrace();
            showCustomMsgPopup("오류", "저장 중 오류가 발생했습니다.\n" + ex.getMessage());
        }
    }

    // [수정됨] parseDateTime: 예외 발생 시 Custom Popup을 띄우고 null을 반환합니다.
    private LocalDateTime parseDateTime(String text, String label) {
        // [수정] 빈 문자열은 null 반환 (빈 문자열이 허용된다고 가정)
        if (text == null || text.isEmpty()) return null;
        try {
            return LocalDateTime.parse(text, FMT);
        } catch (DateTimeParseException e) {
            showCustomMsgPopup(
                    "형식 오류",
                    label + " 형식이 올바르지 않습니다.\n예: 2025-12-08 12:00"
            );
            // 🔥 예외를 던지지 않고 null을 반환하여 onSave 메소드의 실행 흐름을 통제합니다.
            return null; 
        }
    }
    
    // [추가] 커스텀 메시지 팝업 구현 (다른 프레임과 디자인 통일)
    private void showCustomMsgPopup(String title, String msg) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setUndecorated(true);
        dialog.setSize(400, 250);
        dialog.setLocationRelativeTo(this);
        dialog.setBackground(new Color(0, 0, 0, 0));

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(POPUP_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
                g2.setColor(BROWN);
                g2.setStroke(new BasicStroke(3));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 30, 30);
            }
        };
        panel.setLayout(null);
        dialog.add(panel);

        // 폰트 적용 및 중앙 정렬을 위한 JTextPane
        JTextPane msgPane = new JTextPane();
        msgPane.setText(msg);
        msgPane.setFont(uiFont.deriveFont(16f)); // 팝업 메시지는 16f로 설정
        msgPane.setForeground(BROWN);
        msgPane.setOpaque(false);
        msgPane.setEditable(false);

        // 문단 중앙 정렬
        StyledDocument doc = msgPane.getStyledDocument();
        SimpleAttributeSet center = new SimpleAttributeSet();
        StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
        doc.setParagraphAttributes(0, doc.getLength(), center, false);

        msgPane.setBounds(20, 80, 360, 80); // 위치 조정
        panel.add(msgPane);

        JButton okBtn = new JButton("확인");
        okBtn.setFont(uiFont.deriveFont(16f));
        okBtn.setBackground(BROWN);
        okBtn.setForeground(Color.WHITE);
        okBtn.setBounds(135, 170, 130, 45);
        okBtn.setFocusPainted(false);
        okBtn.addActionListener(e -> dialog.dispose());
        panel.add(okBtn);

        dialog.setVisible(true);
    }
}