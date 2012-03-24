/*
 *  MediathekView
 *  Copyright (C) 2008 W. Xaver
 *  W.Xaver[at]googlemail.com
 *  http://zdfmediathk.sourceforge.net/
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package mediathek.controller.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.LinkedList;
import mediathek.Daten;
import mediathek.Konstanten;
import mediathek.Log;

public class GetUrl {

    public static final long UrlWartenBasis = 500;//ms, Basiswert zu dem dann der Faktor multipliziert wird
    private int faktorWarten = 1;
    private int timeout = 10000;
    private long wartenBasis = UrlWartenBasis;
    private static LinkedList<Seitenzaehler> listeSeitenZaehler = new LinkedList<Seitenzaehler>();
    private static LinkedList<Seitenzaehler> listeSeitenZaehlerFehler = new LinkedList<Seitenzaehler>();

    private class Seitenzaehler {

        String senderName = "";
        int seitenAnzahl = 0;

        public Seitenzaehler(String ssenderName) {
            senderName = ssenderName;
            seitenAnzahl = 1;
        }
    }

    public GetUrl() {
    }

    public GetUrl(int ttimeout, long wwartenBasis) {
        timeout = ttimeout;
        wartenBasis = wwartenBasis;
    }

    public GetUrl(long wwartenBasis) {
        wartenBasis = wwartenBasis;
    }

    //===================================
    // public
    //===================================
    public StringBuffer getUri_Utf(String sender, String addr, StringBuffer seite, String meldung) {
        return getUri(sender, addr, seite, Konstanten.KODIERUNG_UTF, timeout, meldung);
    }

    public StringBuffer getUri_Iso(String sender, String addr, StringBuffer seite, String meldung) {
        return getUri(sender, addr, seite, Konstanten.KODIERUNG_ISO15, timeout, meldung);
    }

    public static int getSeitenZaehler(String sender) {
        Iterator<Seitenzaehler> it = listeSeitenZaehler.iterator();
        Seitenzaehler sz;
        while (it.hasNext()) {
            sz = it.next();
            if (sz.senderName.equals(sender)) {
                return sz.seitenAnzahl;
            }
        }
        return 0;
    }

    public static synchronized int getSeitenZaehler() {
        int ret = 0;
        Iterator<Seitenzaehler> it = listeSeitenZaehler.iterator();
        while (it.hasNext()) {
            ret += it.next().seitenAnzahl;
        }
        return ret;
    }

    public static int getSeitenZaehlerFehler(String sender) {
        Iterator<Seitenzaehler> it = listeSeitenZaehlerFehler.iterator();
        Seitenzaehler sz;
        while (it.hasNext()) {
            sz = it.next();
            if (sz.senderName.equals(sender)) {
                return sz.seitenAnzahl;
            }
        }
        return 0;
    }

//    public static synchronized int getSeitenZaehlerFehler() {
//        int ret = 0;
//        Iterator<Seitenzaehler> it = listeSeitenZaehlerFehler.iterator();
//        while (it.hasNext()) {
//            ret += it.next().seitenAnzahl;
//        }
//        return ret;
//    }

    public static synchronized void resetSeitenZaehler() {
        listeSeitenZaehler.clear();
        listeSeitenZaehlerFehler.clear();
    }

    //===================================
    // private
    //===================================
    private synchronized void incSeitenZaehler(String sender) {
        boolean gefunden = false;
        Iterator<Seitenzaehler> it = listeSeitenZaehler.iterator();
        Seitenzaehler sz;
        while (it.hasNext()) {
            sz = it.next();
            if (sz.senderName.equals(sender)) {
                ++sz.seitenAnzahl;
                gefunden = true;
                break;
            }
        }
        if (!gefunden) {
            listeSeitenZaehler.add(new Seitenzaehler(sender));
        }
    }

    private synchronized void incSeitenZaehlerFehler(String sender) {
        boolean gefunden = false;
        Iterator<Seitenzaehler> it = listeSeitenZaehlerFehler.iterator();
        Seitenzaehler sz;
        while (it.hasNext()) {
            sz = it.next();
            if (sz.senderName.equals(sender)) {
                ++sz.seitenAnzahl;
                gefunden = true;
                break;
            }
        }
        if (!gefunden) {
            listeSeitenZaehlerFehler.add(new Seitenzaehler(sender));
        }
    }

    private synchronized StringBuffer getUri(String sender, String addr, StringBuffer seite, String kodierung, int timeout, String meldung) {
        char[] zeichen = new char[1];
        try {
            long w = wartenBasis * faktorWarten;
            this.wait(w);
        } catch (Exception ex) {
            Log.fehlerMeldung("GetUrl.getUri", ex);
        }
        incSeitenZaehler(sender);
        seite.setLength(0);
        URLConnection conn;
        InputStream in = null;
        InputStreamReader inReader = null;
        try {
            URL url = new URL(addr);
            conn = url.openConnection();
            conn.setRequestProperty("User-Agent", Daten.getUserAgent());
            if (timeout > 0) {
                conn.setReadTimeout(timeout);
                conn.setConnectTimeout(timeout);
            }
            in = conn.getInputStream();
            inReader = new InputStreamReader(in, kodierung);
            while (!Daten.filmeLaden.getStop() && inReader.read(zeichen) != -1) {
                seite.append(zeichen);
            }
        } catch (Exception ex) {
            incSeitenZaehlerFehler(sender);
            if (!meldung.equals("")) {
                Log.fehlerMeldung("GetUrl.getUri für: ", meldung);
            }
            Log.fehlerMeldung("GetUrl.getUri", ex, addr);
        } finally {
            try {
                if (in != null) {
                    inReader.close();
                }
            } catch (IOException ex) {
            }
        }
        return seite;
    }
}