package kinoko.server.event;

import kinoko.server.field.ChannelFieldStorage;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public final class Elevator extends Event {
    public static final int HELIOS_TOWER_2ND_FLOOR = 222020100;
    public static final int HELIOS_TOWER_99TH_FLOOR = 222020200;

    public static final int ELEVATOR_TO_LUDIBRIUM = 222020110;
    public static final int ELEVATOR_TO_LUDIBRIUM_GOING_UP = 222020111;
    public static final int ELEVATOR_TO_KOREAN_FOLK_TOWN = 222020210;
    public static final int ELEVATOR_TO_KOREAN_FOLK_TOWN_GOING_DOWN = 222020211;

    public static final int ELEVATOR_DOOR_REACTOR = 2208004;


    public Elevator(ChannelFieldStorage fieldStorage) {
        super(fieldStorage);
    }

    @Override
    public String getIdentifier() {
        return "elevator";
    }

    @Override
    public void initialize() {
        // Initialize current state
        final LocalDateTime now = LocalDateTime.now();
        switch (now.getMinute() % 4) {
            case 0 -> handleElevatorGoingDown();
            case 1 -> handleElevator2ndFloor();
            case 2 -> handleElevatorGoingUp();
            default -> handleElevator99thFloor();
        }
        // Schedule event - run every minute
        final LocalDateTime nextStateTime = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        eventFuture = EventScheduler.addFixedDelayEvent(this::nextState, now.until(nextStateTime, ChronoUnit.MILLIS), 60 * 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void nextState() {
        switch (currentState) {
            case ELEVATOR_GOING_DOWN -> {
                handleElevator2ndFloor();
            }
            case ELEVATOR_2ND_FLOOR -> {
                handleElevatorGoingUp();
            }
            case ELEVATOR_GOING_UP -> {
                handleElevator99thFloor();
            }
            case ELEVATOR_99TH_FLOOR -> {
                handleElevatorGoingDown();
            }
            default -> {
                log.error("Incorrect state for LudibriumElevator : {}", currentState);
            }
        }
    }

    private void handleElevatorGoingDown() {
        currentState = EventState.ELEVATOR_GOING_DOWN;
        warp(ELEVATOR_TO_KOREAN_FOLK_TOWN, ELEVATOR_TO_KOREAN_FOLK_TOWN_GOING_DOWN, "sp");
        setReactorState(HELIOS_TOWER_2ND_FLOOR, ELEVATOR_DOOR_REACTOR, 1);
        setReactorState(HELIOS_TOWER_99TH_FLOOR, ELEVATOR_DOOR_REACTOR, 1);
    }

    private void handleElevator2ndFloor() {
        currentState = EventState.ELEVATOR_2ND_FLOOR;
        warp(ELEVATOR_TO_KOREAN_FOLK_TOWN_GOING_DOWN, HELIOS_TOWER_2ND_FLOOR, "sp");
        setReactorState(HELIOS_TOWER_2ND_FLOOR, ELEVATOR_DOOR_REACTOR, 0);
        setReactorState(HELIOS_TOWER_99TH_FLOOR, ELEVATOR_DOOR_REACTOR, 1);
    }

    private void handleElevatorGoingUp() {
        currentState = EventState.ELEVATOR_GOING_UP;
        warp(ELEVATOR_TO_LUDIBRIUM, ELEVATOR_TO_LUDIBRIUM_GOING_UP, "sp");
        setReactorState(HELIOS_TOWER_2ND_FLOOR, ELEVATOR_DOOR_REACTOR, 1);
        setReactorState(HELIOS_TOWER_99TH_FLOOR, ELEVATOR_DOOR_REACTOR, 1);
    }

    private void handleElevator99thFloor() {
        currentState = EventState.ELEVATOR_99TH_FLOOR;
        warp(ELEVATOR_TO_LUDIBRIUM_GOING_UP, HELIOS_TOWER_99TH_FLOOR, "sp");
        setReactorState(HELIOS_TOWER_2ND_FLOOR, ELEVATOR_DOOR_REACTOR, 1);
        setReactorState(HELIOS_TOWER_99TH_FLOOR, ELEVATOR_DOOR_REACTOR, 0);
    }
}
