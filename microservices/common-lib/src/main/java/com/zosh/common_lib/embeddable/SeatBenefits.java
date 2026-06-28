package com.nikhil.common_lib.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatBenefits {

    @Column(name = "extra_seat_space", nullable = false)
    @Builder.Default
    private Boolean extraSeatSpace = false;

    @Column(name = "preferred_seat_choice", nullable = false)
    @Builder.Default
    private Boolean preferredSeatChoice = false;

    @Column(name = "advance_seat_selection", nullable = false)
    @Builder.Default
    private Boolean advanceSeatSelection = false;

    @Column(name = "guaranteed_seat_together", nullable = false)
    @Builder.Default
    private Boolean guaranteedSeatTogether = false;
}
