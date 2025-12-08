package beehub;

public class MyActivityDTO {
    private String eventName;
    private String eventDate; // 화면 표시용 날짜 (YYYY-MM-DD HH:MM)
    private String location;

    public MyActivityDTO(String eventName, String eventDate, String location) {
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.location = location;
    }

    public String getEventName() { return eventName; }
    public String getEventDate() { return eventDate; }
    public String getLocation() { return location; }
}