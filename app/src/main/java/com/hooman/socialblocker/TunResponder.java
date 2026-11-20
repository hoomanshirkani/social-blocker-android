package com.hooman.socialblocker;

/**
 * پاسخ فوری به بسته‌هایی که به تونل فایروال می‌رسند، تا اپ‌ها «هنگ» نکنند:
 *  - TCP  → بسته RST (اتصال فوراً رد می‌شود به‌جای انتظار تا timeout)
 *  - DNS (UDP/53) → پاسخ REFUSED (جستجوی نام فوراً شکست می‌خورد)
 *  - بقیه UDP (مثل QUIC) → ICMP «port unreachable»
 *  - بقیه → دور ریخته می‌شوند
 * IPv4 و IPv6 هر دو پشتیبانی می‌شوند. هیچ بسته‌ای به بیرون فرستاده نمی‌شود.
 */
final class TunResponder {
    private TunResponder() {}

    private static final int TCP = 6, UDP = 17, ICMP4 = 1, ICMP6 = 58;
    private static final int FIN = 0x01, SYN = 0x02, RST = 0x04, ACK = 0x10;

    /** @return طول پاسخ نوشته‌شده در out، یا 0 اگر پاسخی لازم نیست. */
    static int respond(byte[] p, int len, byte[] out) {
        if (len < 20) return 0;
        int version = (p[0] >> 4) & 0xF;
        try {
            if (version == 4) return v4(p, len, out);
            if (version == 6) return v6(p, len, out);
        } catch (ArrayIndexOutOfBoundsException ignored) {
            // بسته ناقص
        }
        return 0;
    }

    // ------------------------------------------------------------------ IPv4

    private static int v4(byte[] p, int len, byte[] out) {
        int ihl = (p[0] & 0xF) * 4;
        int total = Math.min(u16(p, 2), len);
        int proto = p[9] & 0xFF;
        if (ihl < 20 || total < ihl + 8) return 0;
        int l4 = ihl, l4len = total - ihl;

        if (proto == TCP) {
            int n = tcpRst(p, l4, l4len, out, 20);
            if (n == 0) return 0;
            ipv4Header(out, 20 + n, TCP, p, 16, 12);
            tcpChecksum(out, 20, n, out, 12, 16, 4);
            return 20 + n;
        }
        if (proto == UDP && u16(p, l4 + 2) == 53) {
            int n = dnsRefused(p, l4, l4len, out, 20);
            if (n == 0) return 0;
            ipv4Header(out, 20 + n, UDP, p, 16, 12);
            // UDP checksum در IPv4 اختیاری است (صفر = بدون checksum)
            return 20 + n;
        }
        if (proto == UDP) {
            // ICMP Destination Unreachable / Port Unreachable + هدر IP اصلی + ۸ بایت اول
            int quote = Math.min(total, ihl + 8);
            int n = 8 + quote;
            out[20] = 3; out[21] = 3;
            put16(out, 22, 0);
            put32(out, 24, 0);
            System.arraycopy(p, 0, out, 28, quote);
            put16(out, 22, checksum(out, 20, n, 0));
            ipv4Header(out, 20 + n, ICMP4, p, 16, 12);
            return 20 + n;
        }
        return 0;
    }

    private static void ipv4Header(byte[] o, int total, int proto, byte[] in, int newSrcOff, int newDstOff) {
        o[0] = 0x45;
        o[1] = 0;
        put16(o, 2, total);
        put16(o, 4, 0);          // id
        o[6] = 0x40; o[7] = 0;   // Don't Fragment
        o[8] = 64;               // TTL
        o[9] = (byte) proto;
        put16(o, 10, 0);
        System.arraycopy(in, newSrcOff, o, 12, 4); // مبدأ جدید = مقصد قبلی
        System.arraycopy(in, newDstOff, o, 16, 4); // مقصد جدید = مبدأ قبلی
        put16(o, 10, checksum(o, 0, 20, 0));
    }

    // ------------------------------------------------------------------ IPv6

    private static int v6(byte[] p, int len, byte[] out) {
        if (len < 48) return 0;
        int next = p[6] & 0xFF; // هدرهای توسعه‌ای پشتیبانی نمی‌شوند (نادر) → دور ریخته می‌شوند
        int l4len = Math.min(u16(p, 4), len - 40);
        int l4 = 40;

        int n;
        int proto;
        if (next == TCP) {
            n = tcpRst(p, l4, l4len, out, 40);
            proto = TCP;
        } else if (next == UDP && u16(p, l4 + 2) == 53) {
            n = dnsRefused(p, l4, l4len, out, 40);
            proto = UDP;
        } else if (next == UDP) {
            // ICMPv6 Destination Unreachable / Port Unreachable + تا جای ممکن از بسته اصلی
            int quote = Math.min(Math.min(len, 40 + l4len), 1280 - 48);
            n = 8 + quote;
            out[40] = 1; out[41] = 4;
            put16(out, 42, 0);
            put32(out, 44, 0);
            System.arraycopy(p, 0, out, 48, quote);
            proto = ICMP6;
        } else {
            return 0;
        }
        if (n == 0) return 0;

        out[0] = 0x60; out[1] = 0; out[2] = 0; out[3] = 0;
        put16(out, 4, n);
        out[6] = (byte) proto;
        out[7] = 64;
        System.arraycopy(p, 24, out, 8, 16);  // مبدأ جدید = مقصد قبلی
        System.arraycopy(p, 8, out, 24, 16);  // مقصد جدید = مبدأ قبلی
        // checksum لایه ۴ در IPv6 اجباری است
        int ckOff = proto == TCP ? 16 : proto == UDP ? 6 : 2;
        put16(out, 40 + ckOff, 0);
        put16(out, 40 + ckOff, l4Checksum(out, 40, n, out, 8, 24, 16, proto));
        if (proto == UDP && u16(out, 40 + 6) == 0) put16(out, 40 + 6, 0xFFFF);
        return 40 + n;
    }

    // ------------------------------------------------------------------ TCP RST

    /** هدر TCP پاسخ RST را از off در out می‌نویسد؛ طول آن (۲۰) یا 0. */
    private static int tcpRst(byte[] p, int t, int tlen, byte[] out, int off) {
        if (tlen < 20) return 0;
        int flags = p[t + 13] & 0xFF;
        if ((flags & RST) != 0) return 0; // هرگز به RST پاسخ نده
        int dataOff = ((p[t + 12] >> 4) & 0xF) * 4;
        int payload = Math.max(0, tlen - dataOff);
        long seq = u32(p, t + 4), ack = u32(p, t + 8);

        put16(out, off, u16(p, t + 2));     // پورت مبدأ = پورت مقصد قبلی
        put16(out, off + 2, u16(p, t));     // پورت مقصد = پورت مبدأ قبلی
        if ((flags & ACK) != 0) {
            put32(out, off + 4, ack);
            put32(out, off + 8, 0);
            out[off + 13] = (byte) RST;
        } else {
            long a = seq + payload + ((flags & SYN) != 0 ? 1 : 0) + ((flags & FIN) != 0 ? 1 : 0);
            put32(out, off + 4, 0);
            put32(out, off + 8, a & 0xFFFFFFFFL);
            out[off + 13] = (byte) (RST | ACK);
        }
        out[off + 12] = 0x50; // طول هدر = ۲۰
        put16(out, off + 14, 0); // window
        put16(out, off + 16, 0); // checksum (بعداً)
        put16(out, off + 18, 0); // urgent
        return 20;
    }

    // ------------------------------------------------------------------ DNS REFUSED

    /** پاسخ DNS با کد REFUSED (UDP هدر + پیام) از off در out؛ طول یا 0. */
    private static int dnsRefused(byte[] p, int u, int ulen, byte[] out, int off) {
        int d = u + 8, dlen = ulen - 8;
        if (dlen < 12) return 0;
        if ((p[d + 2] & 0x80) != 0) return 0; // خودش پاسخ است
        int qd = u16(p, d + 4);
        // طول بخش سؤال را پیدا کن (پرسش‌ها فشرده‌سازی ندارند)
        int i = d + 12, end = d + dlen;
        for (int q = 0; q < qd; q++) {
            while (i < end && (p[i] & 0xFF) != 0) {
                int l = p[i] & 0xFF;
                if ((l & 0xC0) != 0) return 0;
                i += l + 1;
            }
            i += 1 + 4; // صفر پایانی + QTYPE + QCLASS
            if (i > end) return 0;
        }
        int msgLen = i - d;
        if (off + 8 + msgLen > out.length) return 0;

        System.arraycopy(p, d, out, off + 8, msgLen);
        int m = off + 8;
        out[m + 2] = (byte) (0x80 | (p[d + 2] & 0x79)); // QR=1، opcode و RD حفظ می‌شود
        out[m + 3] = (byte) 0x85;                        // RA=1، RCODE=5 (REFUSED)
        put16(out, m + 6, 0);  // ANCOUNT
        put16(out, m + 8, 0);  // NSCOUNT
        put16(out, m + 10, 0); // ARCOUNT

        put16(out, off, u16(p, u + 2));     // پورت مبدأ = ۵۳
        put16(out, off + 2, u16(p, u));     // پورت مقصد = پورت درخواست‌کننده
        put16(out, off + 4, 8 + msgLen);
        put16(out, off + 6, 0);
        return 8 + msgLen;
    }

    // ------------------------------------------------------------------ checksums

    private static void tcpChecksum(byte[] o, int seg, int segLen, byte[] ip, int srcOff, int dstOff, int addrLen) {
        put16(o, seg + 16, 0);
        put16(o, seg + 16, l4Checksum(o, seg, segLen, ip, srcOff, dstOff, addrLen, TCP));
    }

    private static int l4Checksum(byte[] o, int seg, int segLen, byte[] ip, int srcOff, int dstOff,
                                  int addrLen, int proto) {
        long sum = 0;
        sum += sumWords(ip, srcOff, addrLen);
        sum += sumWords(ip, dstOff, addrLen);
        if (addrLen == 4) {
            sum += proto;
            sum += segLen;
        } else {
            sum += (segLen >>> 16) & 0xFFFF;
            sum += segLen & 0xFFFF;
            sum += proto;
        }
        sum += sumWords(o, seg, segLen);
        return fold(sum);
    }

    private static int checksum(byte[] b, int off, int len, long initial) {
        return fold(initial + sumWords(b, off, len));
    }

    private static long sumWords(byte[] b, int off, int len) {
        long sum = 0;
        int i = off, end = off + len;
        for (; i + 1 < end; i += 2) sum += ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
        if (i < end) sum += (b[i] & 0xFF) << 8;
        return sum;
    }

    private static int fold(long sum) {
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }

    // ------------------------------------------------------------------ bytes

    private static int u16(byte[] b, int o) { return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF); }

    private static long u32(byte[] b, int o) {
        return ((long) (b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    private static void put16(byte[] b, int o, int v) {
        b[o] = (byte) (v >> 8);
        b[o + 1] = (byte) v;
    }

    private static void put32(byte[] b, int o, long v) {
        b[o] = (byte) (v >> 24);
        b[o + 1] = (byte) (v >> 16);
        b[o + 2] = (byte) (v >> 8);
        b[o + 3] = (byte) v;
    }
}
