package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.controller.ApiException;
import co.tkm.ticketmanagementsystem.enums.SeatType;
import co.tkm.ticketmanagementsystem.model.ShowtimeSeat;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The couple-seat rule shared by hold and checkout: couple seats sell only as
 * same-row {@code (odd, odd+1)} pairs such as {@code A1+A2}.
 */
final class CouplePairs {

    static final String COUPLE_PAIR_INVALID = "COUPLE_PAIR_INVALID";
    private static final String MESSAGE = "Couple seats must be selected in pairs.";
    private static final Pattern SEAT_CODE_PATTERN = Pattern.compile("^([A-Za-z]+)(\\d+)$");

    private CouplePairs() {
    }

    /** Number of couple seats among {@code seats}, after checking they form valid pairs. */
    static int validate(Collection<ShowtimeSeat> seats) {
        List<SeatCode> couples = new ArrayList<>();
        for (ShowtimeSeat ss : seats) {
            if (ss.getSeat() != null && ss.getSeat().getSeatType() == SeatType.COUPLE) {
                couples.add(parse(ss.getSeat().getSeatCode()));
            }
        }
        validateCodes(couples);
        return couples.size();
    }

    private static void validateCodes(List<SeatCode> couples) {
        if (couples.isEmpty()) return;
        if (couples.size() % 2 != 0) {
            throw invalid();
        }
        Map<String, List<Integer>> numbersByRow = new HashMap<>();
        for (SeatCode seat : couples) {
            numbersByRow.computeIfAbsent(seat.row(), ignored -> new ArrayList<>()).add(seat.number());
        }
        for (List<Integer> rowNumbers : numbersByRow.values()) {
            rowNumbers.sort(Comparator.naturalOrder());
            if (rowNumbers.size() % 2 != 0) {
                throw invalid();
            }
            for (int i = 0; i < rowNumbers.size(); i += 2) {
                int first = rowNumbers.get(i);
                int second = rowNumbers.get(i + 1);
                if (second != first + 1 || (first % 2 == 0)) {
                    throw invalid();
                }
            }
        }
    }

    private static SeatCode parse(String seatCode) {
        if (seatCode == null) {
            throw invalid();
        }
        Matcher matcher = SEAT_CODE_PATTERN.matcher(seatCode.trim());
        if (!matcher.matches()) {
            throw invalid();
        }
        return new SeatCode(matcher.group(1).toUpperCase(), Integer.parseInt(matcher.group(2)));
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, COUPLE_PAIR_INVALID, MESSAGE);
    }

    private record SeatCode(String row, int number) {
    }
}
