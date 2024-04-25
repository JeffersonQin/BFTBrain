package com.gbft.framework.utils;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TreeSet;

import com.gbft.framework.coordination.CoordinatorUnit;
import com.gbft.framework.data.Event;
import com.gbft.framework.data.MessageData;
import com.gbft.framework.data.ReportData;
import com.gbft.framework.data.RequestData;
import com.gbft.framework.data.RequestData.Operation;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.statemachine.Transition;
import com.google.protobuf.ByteString;

public class Printer {

    public static int verbosity;
    private static Writer out;
    private static DateTimeFormatter briefFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss ");
    private static DateTimeFormatter verboseFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS ");

    public static void init() {
        verbosity = Config.string("general.verbosity").length();

        try {
            var stream = new FileOutputStream(java.io.FileDescriptor.out);

            if (Config.bool("general.logfile")) {
                stream = new FileOutputStream("logs/" + briefFormatter.format(LocalDateTime.now()) + "u" + CoordinatorUnit.unit_id + ".log");
            }
            
            if (verbosity <= Verbosity.VVV) {
                out = new BufferedWriter(new OutputStreamWriter(stream, "ASCII"), 1024);
            } else {
                out = new BufferedWriter(new OutputStreamWriter(stream), 1024);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void print(int verbosity, String prefix, String text) {
        if (verbosity > Printer.verbosity) {
            return;
        }

        var sb = new StringBuilder(datestr());
        if (prefix != null) {
            sb.append(prefix);
        }

        sb.append(text);
        sb.append('\n');

        try {
            out.write(sb.toString());
        } catch (IOException e) {
        }
    }

    public static void print(int verbosity, String prefix, String text, Event event) {
        if (verbosity > Printer.verbosity) {
            return;
        }

        var sb = new StringBuilder(datestr());
        if (prefix != null) {
            sb.append(prefix);
        }

        sb.append(text);

        sb.append(event.getEventType());

        sb.append('\n');

        try {
            out.write(sb.toString());
        } catch (IOException e) {
        }
    }

    // TODO: Allow inserting message str into text.
    public static void print(int verbosity, String prefix, String text, MessageData message) {
        if (verbosity > Printer.verbosity) {
            return;
        }

        var sb = new StringBuilder(datestr());
        if (prefix != null) {
            sb.append(prefix);
        }

        sb.append(text);

        var type = message.getMessageType();
        var typestr = StateMachine.messages.get(type).name.toUpperCase();
        sb.append(typestr).append(" ");
        if (type == StateMachine.REQUEST) {
            sb.append(message.getRequests(0).getRequestNum());
        } else {
            sb.append(message.getSequenceNum());
        }
        sb.append(":").append(message.getViewNum());

        sb.append(" from ").append(message.getSource());
        sb.append(" to ").append(message.getTargetsList());
        // sb.append(" debug ").append(bytestr(message.getDigest()));

        sb.append('\n');

        try {
            out.write(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void print(int verbosity, String prefix, String text, RequestData request) {
        if (verbosity > Printer.verbosity) {
            return;
        }

        var sb = new StringBuilder(datestr());
        if (prefix != null) {
            sb.append(prefix);
        }

        sb.append(text);

        sb.append('#').append(request.getRequestNum());
        sb.append(" by client").append(request.getClient());

        if (Printer.verbosity > Verbosity.VV) {
            var op = request.getOperation();
            sb.append(" [").append(op).append(' ');
            if (op == Operation.ADD) {
                sb.append(request.getValue()).append(" to ");
            } else if (op == Operation.SUB) {
                sb.append(request.getValue()).append(" from ");
            }
            sb.append(request.getRecord()).append(']');
        }

        sb.append('\n');

        try {
            out.write(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void print(int verbosity, String prefix, String text, Transition transition) {
        if (verbosity > Printer.verbosity) {
            return;
        }

        var sb = new StringBuilder(datestr());
        if (prefix != null) {
            sb.append(prefix);
        }

        sb.append(text);

        sb.append(" from ");
        sb.append(StateMachine.states.get(transition.fromState).name);
        sb.append(" to ");
        sb.append(StateMachine.states.get(transition.toState).name);
        sb.append(", with response ");
        for (var pair : transition.responses) {
            var role = pair.getLeft();
            var messageType = pair.getRight();
            sb.append(StateMachine.messages.get(messageType).name.toUpperCase());
            sb.append(" to ");
            sb.append(StateMachine.roles.get(role).toUpperCase());
            sb.append(" ");
        }

        sb.append('\n');

        try {
            out.write(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final String[] pads = new String[] {
            "        \t",
            "       \t",
            "      \t",
            "     \t",
            "    \t",
            "   \t",
            "  \t",
            " \t" };

    public static String convertToString(ReportData report) {
        var builder = new StringBuilder();
        var items = report.getReportDataMap();
        var keys = new TreeSet<>(items.keySet());
        for (var entity : keys) {
            builder.append("-- ");
            builder.append(entity);
            builder.append("\n");
            var item = items.get(entity).getItemDataMap();
            for (var measure : item.keySet().stream().sorted().toList()) {
                var value = item.get(measure);
                builder.append(measure);
                if (measure.length() > 15) {
                    builder.append("\t");
                } else if (measure.length() > 8) {
                    builder.append(pads[measure.length() - 8]);
                } else {
                    builder.append("\t\t\t");
                }
                builder.append(value);
                builder.append("\n");
            }
        }
        builder.append("\n");

        return builder.toString();
    }

    public static String convertToString(MessageData message) {
        var sb = new StringBuilder();
        var type = message.getMessageType();
        var typestr = StateMachine.messages.get(type).name.toUpperCase();
        sb.append(typestr).append(" ");
        if (type == StateMachine.REQUEST) {
            sb.append(message.getRequests(0).getRequestNum());
        } else {
            sb.append(message.getSequenceNum());
        }
        sb.append(":").append(message.getViewNum());

        sb.append(" from ").append(message.getSource());
        sb.append(" to ").append(message.getTargetsList());

        return sb.toString();
    }

    public static String timeFormat(long t, boolean fractions) {
        if (t < 0) {
            return '-' + timeFormat(-t, fractions);
        } else if (t == 0) {
            return "0";
        }

        long wholenum;
        long fraction;
        String postfix;

        if (t > 1000000000L) {
            wholenum = t / 1000000000L;
            fraction = t % 1000000000L / 10000000;
            postfix = "s";
        } else if (t > 1000000) {
            wholenum = t / 1000000;
            fraction = t % 1000000 / 10000;
            postfix = "ms";
        } else if (t > 1000) {
            wholenum = t / 1000;
            fraction = (t % 1000) / 10;
            postfix = "us";
        } else {
            wholenum = t;
            fraction = 0;
            postfix = "ns";
        }

        var sb = new StringBuilder().append(wholenum);
        if (fractions && t > 1000) {
            sb.append('.');
            if (fraction < 10) {
                sb.append('0');
            }
            sb.append(fraction);
        }
        sb.append(postfix);
        return sb.toString();
    }

    private static String datestr() {
        if (Printer.verbosity > Verbosity.VV) {
            var now = LocalDateTime.now();
            return verboseFormatter.format(now);
        } else if (Printer.verbosity > Verbosity.V) {
            var now = LocalDateTime.now();
            return briefFormatter.format(now);
        }

        return "";
    }

    private final static char[] HEXMAP = "0123456789ABCDEF".toCharArray();

    public static String bytestr(ByteString data) {
        var bytes = data.toByteArray();
        if (bytes.length == 0) {
            return "NULL";
        }

        StringBuilder sb = new StringBuilder();
        for (var i = 0; i < 4; i++) {
            sb.append(HEXMAP[(0xF0 & bytes[i]) >>> 4]);
            sb.append(HEXMAP[(0x0F & bytes[i])]);
        }

        return sb.toString();
    }

    public static class Verbosity {
        public static final int V = 1;
        public static final int VV = 2;
        public static final int VVV = 3;
        public static final int VVVV = 4;
        public static final int VVVVV = 5;
        public static final int VVVVVV = 6;
    }
}
