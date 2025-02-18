package io.github.venkat1701.websitevisitcounter.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Table(name="web_counter")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WebsiteCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String url;
    private String counter;
    private String method;
}
