import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Alarm Clock System
 * Supports one-time and recurring alarms with snooze functionality.
 */
public class AlarmClock {

    // ─── Alarm Model ────────────────────────────────────────────────────────────

    enum RepeatMode { ONCE, DAILY, WEEKDAYS, WEEKENDS, CUSTOM }

    static class Alarm {
        private static int idCounter = 1;

        final int id;
        String label;
        LocalTime time;
        RepeatMode repeatMode;
        Set<DayOfWeek> customDays;   // used when repeatMode == CUSTOM
        boolean enabled;
        int snoozeMinutes;

        Alarm(String label, LocalTime time, RepeatMode repeatMode) {
            this.id           = idCounter++;
            this.label        = label;
            this.time         = time;
            this.repeatMode   = repeatMode;
            this.customDays   = new HashSet<>();
            this.enabled      = true;
            this.snoozeMinutes = 5;
        }

        /** Returns true if this alarm should fire on the given day. */
        boolean shouldFireOn(DayOfWeek day) {
            return switch (repeatMode) {
                case ONCE     -> true; // checked once then disabled
                case DAILY    -> true;
                case WEEKDAYS -> day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
                case WEEKENDS -> day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
                case CUSTOM   -> customDays.contains(day);
            };
        }

        @Override
        public String toString() {
            String days = repeatMode == RepeatMode.CUSTOM
                    ? " [" + customDays + "]"
                    : "";
            return String.format("[%d] %-20s %s  %-10s%s  %s  snooze=%dmin",
                    id, label,
                    time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    repeatMode, days,
                    enabled ? "ON " : "OFF",
                    snoozeMinutes);
        }
    }

    // ─── AlarmManager ───────────────────────────────────────────────────────────

    static class AlarmManager {
        private final List<Alarm> alarms = new ArrayList<>();
        private final ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        private static final DateTimeFormatter FMT =
                DateTimeFormatter.ofPattern("HH:mm:ss");

        /** Add a new alarm and schedule it. */
        Alarm addAlarm(String label, LocalTime time, RepeatMode repeatMode) {
            Alarm alarm = new Alarm(label, time, repeatMode);
            alarms.add(alarm);
            scheduleAlarm(alarm);
            System.out.printf("✅ Alarm #%d '%s' set for %s (%s)%n",
                    alarm.id, alarm.label,
                    alarm.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    alarm.repeatMode);
            return alarm;
        }

        /** Remove an alarm by ID. */
        boolean removeAlarm(int id) {
            boolean removed = alarms.removeIf(a -> a.id == id);
            if (removed) System.out.println("🗑️  Alarm #" + id + " removed.");
            else         System.out.println("⚠️  Alarm #" + id + " not found.");
            return removed;
        }

        /** Enable or disable an alarm. */
        void setEnabled(int id, boolean enabled) {
            findById(id).ifPresentOrElse(
                    a -> {
                        a.enabled = enabled;
                        System.out.printf("%s Alarm #%d '%s'%n",
                                enabled ? "▶️  Enabled" : "⏸️  Disabled", id, a.label);
                    },
                    () -> System.out.println("⚠️  Alarm #" + id + " not found.")
            );
        }

        /** Change snooze duration. */
        void setSnooze(int id, int minutes) {
            findById(id).ifPresentOrElse(
                    a -> {
                        a.snoozeMinutes = minutes;
                        System.out.printf("⏰ Alarm #%d snooze set to %d minutes%n", id, minutes);
                    },
                    () -> System.out.println("⚠️  Alarm #" + id + " not found.")
            );
        }

        /** Snooze a currently-firing alarm (simulated: schedules a one-shot re-fire). */
        void snooze(int id) {
            findById(id).ifPresentOrElse(
                    a -> {
                        System.out.printf("💤 Alarm #%d '%s' snoozed for %d minutes%n",
                                id, a.label, a.snoozeMinutes);
                        scheduler.schedule(
                                () -> ring(a, true),
                                a.snoozeMinutes, TimeUnit.MINUTES);
                    },
                    () -> System.out.println("⚠️  Alarm #" + id + " not found.")
            );
        }

        /** List all alarms. */
        void listAlarms() {
            if (alarms.isEmpty()) {
                System.out.println("No alarms set.");
                return;
            }
            System.out.println("\n── Alarms ──────────────────────────────────────────────");
            alarms.forEach(System.out::println);
            System.out.println("────────────────────────────────────────────────────────\n");
        }

        /** Shut down the scheduler gracefully. */
        void shutdown() {
            scheduler.shutdownNow();
            System.out.println("AlarmManager shut down.");
        }

        // ── Internal helpers ──────────────────────────────────────────────────

        private Optional<Alarm> findById(int id) {
            return alarms.stream().filter(a -> a.id == id).findFirst();
        }

        private void scheduleAlarm(Alarm alarm) {
            scheduler.scheduleAtFixedRate(() -> checkAndFire(alarm), 0, 1, TimeUnit.SECONDS);
        }

        private void checkAndFire(Alarm alarm) {
            if (!alarm.enabled) return;

            LocalDateTime now = LocalDateTime.now();
            LocalTime    nowT = now.toLocalTime().withNano(0);

            // Match HH:mm (ignore seconds)
            if (nowT.getHour()   == alarm.time.getHour() &&
                nowT.getMinute() == alarm.time.getMinute() &&
                nowT.getSecond() == 0) {

                if (alarm.shouldFireOn(now.getDayOfWeek())) {
                    ring(alarm, false);

                    if (alarm.repeatMode == RepeatMode.ONCE) {
                        alarm.enabled = false; // fire only once
                    }
                }
            }
        }

        private void ring(Alarm alarm, boolean isSnoozed) {
            String prefix = isSnoozed ? "💤 SNOOZE " : "🔔 ALARM  ";
            System.out.printf("%n%s #%d | '%s' | %s%n",
                    prefix, alarm.id, alarm.label,
                    LocalTime.now().format(FMT));
            // In a real app: play audio, show notification, etc.
        }
    }

    // ─── Demo / main ────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        AlarmManager manager = new AlarmManager();

        System.out.println("=== Alarm Clock Demo ===\n");

        // 1. One-time alarm 5 seconds from now (for quick demo)
        LocalTime fiveSecsLater = LocalTime.now().plusSeconds(5)
                                           .withNano(0);
        Alarm a1 = manager.addAlarm("Wake Up", fiveSecsLater, RepeatMode.ONCE);

        // 2. Daily morning alarm
        Alarm a2 = manager.addAlarm("Morning Stand-up",
                LocalTime.of(9, 0), RepeatMode.DAILY);

        // 3. Weekday-only alarm
        Alarm a3 = manager.addAlarm("Lunch Break",
                LocalTime.of(13, 0), RepeatMode.WEEKDAYS);

        // 4. Custom days alarm (Mon, Wed, Fri)
        Alarm a4 = manager.addAlarm("Gym Session",
                LocalTime.of(7, 30), RepeatMode.CUSTOM);
        a4.customDays.add(DayOfWeek.MONDAY);
        a4.customDays.add(DayOfWeek.WEDNESDAY);
        a4.customDays.add(DayOfWeek.FRIDAY);

        // List all alarms
        manager.listAlarms();

        // Adjust snooze for alarm #1
        manager.setSnooze(a1.id, 10);

        // Disable alarm #3
        manager.setEnabled(a3.id, false);

        // List again to see changes
        manager.listAlarms();

        // Wait long enough to observe alarm #1 firing (~6 seconds)
        System.out.println("Waiting for alarm #" + a1.id + " to fire...\n");
        Thread.sleep(8_000);

        // Remove alarm #2
        manager.removeAlarm(a2.id);

        // Simulate snooze on alarm #4
        manager.snooze(a4.id);

        manager.listAlarms();

        // Clean shutdown
        manager.shutdown();
    }
}
