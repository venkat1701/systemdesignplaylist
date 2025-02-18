package io.github.venkat1701.websitevisitcounter.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.checkerframework.checker.units.qual.A;

public class WebsiteVisitDTO {

    private int visits;
    private String servedVia;

    public WebsiteVisitDTO(int visits, String servedVia) {
        this.visits = visits;
        this.servedVia = servedVia;
    }

    public int getVisits() {
        return visits;
    }

    public void setVisits(int visits) {
        this.visits = visits;
    }

    public String getServedVia() {
        return servedVia;
    }

    public void setServedVia(String servedVia) {
        this.servedVia = servedVia;
    }
}
