package com.example.hanziime;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PinyinEngine {
    private static final int MAX_CANDIDATES = 256;
    private final Map<String, List<Entry>> normalFullIndex = new HashMap<>();
    private final Map<String, List<Entry>> normalInitialIndex = new HashMap<>();

    public PinyinEngine(Context context) {
        this(context, true);
    }

    PinyinEngine(Context context, boolean includeFullDictionary) {
        // The small table covers the gap before the full table loads, and a few
        // hand-tuned rows. A flat bonus would hide the full table's real weights.
        load(openAsset(context, "pinyin_dictionary.tsv"), 0);
        if (includeFullDictionary) load(openAsset(context, "pinyin_rime.tsv"), 0);
    }

    PinyinEngine(Reader source) {
        load(source, 0);
    }

    private void load(Reader source, int priorityBonus) {
        try (BufferedReader reader = new BufferedReader(source)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\\t");
                if (fields.length >= 4) {
                    addEntry(new Entry(normalize(fields[0]), fields[1], fields[2],
                            Integer.parseInt(fields[3]) + priorityBonus));
                }
            }
        } catch (IOException | NumberFormatException error) {
            throw new IllegalStateException("无法加载拼音词库", error);
        }
    }

    private void addEntry(Entry entry) {
        index(normalFullIndex, entry.key, entry);
        index(normalInitialIndex, entry.initials, entry);
    }

    private static void index(Map<String, List<Entry>> index, String value, Entry entry) {
        for (int length = 1; length <= Math.min(2, value.length()); length++) {
            String prefix = value.substring(0, length);
            List<Entry> bucket = index.get(prefix);
            if (bucket == null) {
                bucket = new ArrayList<>();
                index.put(prefix, bucket);
            }
            bucket.add(entry);
        }
    }

    private static Reader openAsset(Context context, String name) {
        try {
            return new InputStreamReader(context.getAssets().open(name), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("无法打开拼音词库：" + name, error);
        }
    }

    public List<Candidate> search(String rawInput, boolean fuzzyEnabled) {
        String query = normalize(rawInput);
        if (query.isEmpty()) return List.of();
        // One letter never takes n↔l, so "l" stays with l-words.
        boolean useFuzzy = fuzzyEnabled && query.length() >= 2;
        Set<String> variants = useFuzzy ? fuzzyVariants(query) : Set.of();
        boolean abbreviation = isAbbreviationQuery(query);
        // "sh" / "zhon" are unfinished syllables. Characters that continue them
        // (是, 中) must beat phrases such as 试试 / 中国.
        boolean partialSyllable = isPartialSyllable(query);
        Set<String> prefixes = new LinkedHashSet<>();
        prefixes.add(query.substring(0, Math.min(2, query.length())));
        for (String variant : variants) {
            prefixes.add(variant.substring(0, Math.min(2, variant.length())));
        }
        Set<Entry> pool = new LinkedHashSet<>();
        for (String prefix : prefixes) {
            List<Entry> fullEntries = normalFullIndex.get(prefix);
            List<Entry> initialEntries = normalInitialIndex.get(prefix);
            if (fullEntries != null) pool.addAll(fullEntries);
            if (initialEntries != null) pool.addAll(initialEntries);
        }
        List<Scored> matches = new ArrayList<>();

        for (Entry entry : pool) {
            String full = entry.key;
            String initials = entry.initials;
            boolean fuzzyExact = variants.contains(full);
            int quality = matchQuality(query, full, initials, abbreviation, fuzzyExact);
            if (quality <= 0) continue;
            // "qua" exactly spells 去啊, but it is still the start of 全/权.
            // An unfinished syllable must not treat that phrase as a finished word.
            if (partialSyllable && full.equals(query) && entry.text.length() > 1) {
                quality = Math.min(quality, 200_000);
            }
            int score = entry.frequency + quality;
            // Exact syllable hits: keep single characters ahead of longer phrases
            // that only prefix-match (e.g. da→打 before 大学/打开).
            if (full.equals(query) && entry.text.length() > 1) {
                score -= 100_000 * (entry.text.length() - 1);
            }
            // Typing "a" must not surface 爱(ai)/安(an) above 啊(a).
            // An unfinished syllable has no exact character, so 是/中 stay up.
            // A one-change fuzzy spelling is already scored below the exact spelling.
            boolean continuesPartial = !fuzzyExact && partialSyllable && entry.text.length() == 1
                    && entry.key.startsWith(query);
            if (continuesPartial) {
                score += 1_600_000;
            } else if (!fuzzyExact && !full.equals(query) && full.startsWith(query)
                    && entry.text.length() == 1) {
                score -= 1_500_000;
            } else if (!fuzzyExact && !full.equals(query) && full.startsWith(query)
                    && entry.text.length() > 1) {
                score -= 50_000 * (entry.text.length() - 1);
                // Single letter: further demote multi-char prefix hits so 了/里
                // can compete with 老师/里面 (no exact syllable "l"), and so
                // core phrases like 方便 do not outrank 发/法 on lone "f".
                if (query.length() == 1) {
                    score -= 2_600_000;
                }
            }
            // A finished syllable that only coincides with initials (de→第二)
            // stays below the exact character. A longer abbreviation
            // (zg→中国人) stays below the exact abbreviation (中国).
            if (!abbreviation && !full.equals(query) && !fuzzyExact
                    && initials.equals(query) && entry.text.length() > 1) {
                score -= 1_800_000;
            } else if (abbreviation && initials.startsWith(query)
                    && !initials.equals(query)) {
                score -= 1_800_000;
            }
            // ng → 那个人 must not outrank 那个. The exact abbreviation is
            // already sunk below 嗯; longer initials sink further.
            if (!abbreviation && initials.startsWith(query)
                    && !initials.equals(query) && entry.text.length() > 1) {
                score -= 1_800_000;
            }
            matches.add(new Scored(entry, score));
        }

        Collections.sort(matches, (left, right) -> {
            int scoreOrder = Integer.compare(right.score, left.score);
            if (scoreOrder != 0) return scoreOrder;
            int lengthOrder = Integer.compare(left.entry.text.length(), right.entry.text.length());
            if (lengthOrder != 0) return lengthOrder;
            return Integer.compare(right.entry.frequency, left.entry.frequency);
        });
        List<Candidate> result = new ArrayList<>();
        Set<String> seenText = new LinkedHashSet<>();
        for (Scored item : matches) {
            if (!seenText.add(item.entry.text)) continue;
            result.add(new Candidate(item.entry.text, item.entry.displayPinyin,
                    item.score, Candidate.Source.PINYIN));
            if (result.size() == MAX_CANDIDATES) break;
        }
        return result;
    }

    private static int matchQuality(String query, String full, String initials,
                                    boolean abbreviation, boolean fuzzyExact) {
        // Exact spelling stays above a one-change fuzzy spelling, even when the
        // fuzzy word is far more frequent. Full-table weights top out near 5e6,
        // so the gap has to clear that.
        if (full.equals(query)) return 6_000_000;
        if (fuzzyExact) return 500_000;
        // 简拼 zg matches 中国 exactly. That has to beat prefix hits such as 中国人,
        // whose full spelling also starts with "zg".
        if (abbreviation && initials.equals(query)) return 1_500_000;
        if (full.startsWith(query)) {
            int extra = full.length() - query.length();
            return 80_000 - extra * 3_000;
        }
        if (query.length() >= 2 && initials.equals(query)) return 200_000;
        if (query.length() >= 2 && initials.startsWith(query)) {
            int extra = initials.length() - query.length();
            return 100_000 - extra * 2_000;
        }
        return 0;
    }

    static String normalize(String input) {
        return input.toLowerCase(Locale.ROOT)
                .replace("ü", "v")
                .replaceAll("[āáǎà]", "a").replaceAll("[ēéěè]", "e")
                .replaceAll("[īíǐì]", "i").replaceAll("[ōóǒò]", "o")
                .replaceAll("[ūúǔù]", "u").replaceAll("[ǖǘǚǜ]", "v")
                .replaceAll("[^a-z]", "");
    }

    /**
     * Spellings that differ from {@code query} by one allowed change on one
     * syllable: z/zh, c/ch, s/sh, n/l, hu/fu, or an/ang, en/eng, in/ing.
     * {@code can} reaches {@code chan} and {@code cang}, not {@code chang}.
     * Empty when the query is a single letter or cannot be split into syllables.
     */
    static Set<String> fuzzyVariants(String query) {
        List<String> parts = segment(query);
        if (parts == null || parts.isEmpty()) return Set.of();
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        for (int index = 0; index < parts.size(); index++) {
            for (String alternative : syllableAlternatives(parts.get(index))) {
                StringBuilder built = new StringBuilder();
                for (int part = 0; part < parts.size(); part++) {
                    built.append(part == index ? alternative : parts.get(part));
                }
                String word = built.toString();
                if (!word.equals(query)) variants.add(word);
            }
        }
        return variants;
    }

    private static List<String> syllableAlternatives(String syllable) {
        List<String> alternatives = new ArrayList<>();
        if (syllable.length() >= 3 && syllable.charAt(1) == 'h'
                && "zcs".indexOf(syllable.charAt(0)) >= 0) {
            addSyllable(alternatives, syllable.charAt(0) + syllable.substring(2));
        } else if (syllable.length() >= 2 && "zcs".indexOf(syllable.charAt(0)) >= 0
                && syllable.charAt(1) != 'h') {
            addSyllable(alternatives, syllable.charAt(0) + "h" + syllable.substring(1));
        }
        if (syllable.length() >= 2 && (syllable.charAt(0) == 'n' || syllable.charAt(0) == 'l')) {
            char other = syllable.charAt(0) == 'n' ? 'l' : 'n';
            addSyllable(alternatives, other + syllable.substring(1));
        }
        if (syllable.equals("hu")) addSyllable(alternatives, "fu");
        else if (syllable.equals("fu")) addSyllable(alternatives, "hu");
        if (syllable.endsWith("ang")) {
            addSyllable(alternatives, syllable.substring(0, syllable.length() - 1));
        } else if (syllable.endsWith("an")) {
            addSyllable(alternatives, syllable + "g");
        } else if (syllable.endsWith("eng")) {
            addSyllable(alternatives, syllable.substring(0, syllable.length() - 1));
        } else if (syllable.endsWith("en")) {
            addSyllable(alternatives, syllable + "g");
        } else if (syllable.endsWith("ing")) {
            addSyllable(alternatives, syllable.substring(0, syllable.length() - 1));
        } else if (syllable.endsWith("in")) {
            addSyllable(alternatives, syllable + "g");
        }
        return alternatives;
    }

    private static void addSyllable(List<String> alternatives, String candidate) {
        if (SYLLABLES.contains(candidate)) alternatives.add(candidate);
    }

    /** Longest-match split. Null when leftover letters are not a syllable. */
    private static List<String> segment(String query) {
        if (query == null || query.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        if (!segmentFrom(query, 0, parts)) return null;
        Collections.reverse(parts);
        return parts;
    }

    private static boolean segmentFrom(String query, int start, List<String> parts) {
        if (start == query.length()) return true;
        int limit = Math.min(query.length(), start + 6);
        for (int end = limit; end > start; end--) {
            String piece = query.substring(start, end);
            boolean syllable = SYLLABLES.contains(piece);
            String base = piece.length() >= 4 && piece.charAt(piece.length() - 1) == 'r'
                    ? piece.substring(0, piece.length() - 1) : "";
            boolean erhua = !syllable && base.length() >= 3 && SYLLABLES.contains(base);
            if ((syllable || erhua) && segmentFrom(query, end, parts)) {
                parts.add(piece);
                return true;
            }
        }
        return false;
    }

    /** First letter of each display syllable: "zhōng guó rén" → "zgr". */
    static String initialsOf(String displayPinyin) {
        if (displayPinyin == null || displayPinyin.isEmpty()) return "";
        StringBuilder initials = new StringBuilder();
        for (String syllable : displayPinyin.split(" ")) {
            String plain = stripToneMarks(syllable);
            if (!plain.isEmpty()) initials.append(plain.charAt(0));
        }
        return initials.toString();
    }

    /**
     * True when the query is a 简拼 initialism (zg, bj, nh, zgr) rather than
     * full pinyin or an unfinished syllable (zh, zhon, de, nihao).
     */
    static boolean isAbbreviationQuery(String query) {
        if (query == null || query.length() < 2) return false;
        if (INCOMPLETE_SYLLABLES.contains(query)) return false;
        return !canSegment(query);
    }

    /** Unfinished syllable: n, sh, zh, zhon. Not a whole syllable and not 简拼. */
    static boolean isPartialSyllable(String query) {
        return query != null && !query.isEmpty() && INCOMPLETE_SYLLABLES.contains(query);
    }

    /** One complete syllable, such as ng, de, or shi. */
    static boolean isSingleSyllable(String query) {
        return query != null && SYLLABLES.contains(query);
    }

    static java.util.Set<String> syllables() {
        return SYLLABLES;
    }

    static java.util.Set<String> incompleteSyllables() {
        return INCOMPLETE_SYLLABLES;
    }

    private static String stripToneMarks(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[āáǎà]", "a").replaceAll("[ēéěè]", "e")
                .replaceAll("[īíǐì]", "i").replaceAll("[ōóǒò]", "o")
                .replaceAll("[ūúǔù]", "u").replaceAll("[ǖǘǚǜü]", "v")
                .replaceAll("[^a-z]", "");
    }

    private static boolean canSegment(String query) {
        Boolean[] memo = new Boolean[query.length() + 1];
        return canSegmentFrom(query, 0, memo);
    }

    private static boolean canSegmentFrom(String query, int start, Boolean[] memo) {
        if (start == query.length()) return true;
        if (memo[start] != null) return memo[start];
        boolean matched = false;
        int limit = Math.min(query.length(), start + 6);
        for (int end = limit; end > start; end--) {
            String piece = query.substring(start, end);
            if (SYLLABLES.contains(piece) && canSegmentFrom(query, end, memo)) {
                matched = true;
                break;
            }
            // 儿化: huir / zher. The base syllable must be at least 3 letters so
            // a 3-letter initialism such as bar is not swallowed as ba+r.
            if (piece.length() >= 4 && piece.charAt(piece.length() - 1) == 'r') {
                String base = piece.substring(0, piece.length() - 1);
                if (base.length() >= 3 && SYLLABLES.contains(base)
                        && canSegmentFrom(query, end, memo)) {
                    matched = true;
                    break;
                }
            }
        }
        memo[start] = matched;
        return matched;
    }

    private static final String SYLLABLE_TEXT = """
            a ai an ang ao
            ba bai ban bang bao bei ben beng bi bian biao bie bin bing bo bu
            ca cai can cang cao ce cen ceng cha chai chan chang chao che chen cheng chi
            chong chou chu chua chuai chuan chuang chui chun chuo ci cong cou cu cuan
            cui cun cuo da dai dan dang dao de dei den deng di dia dian diao die ding
            diu dong dou du duan dui dun duo e ei en eng er fa fan fang fei fen feng
            fiao fo fou fu ga gai gan gang gao ge gei gen geng gong gou gu gua guai
            guan guang gui gun guo ha hai han hang hao he hei hen heng hong hou hu hua
            huai huan huang hui hun huo ji jia jian jiang jiao jie jin jing jiong jiu
            ju juan jue jun ka kai kan kang kao ke ken keng kong kou ku kua kuai kuan
            kuang kui kun kuo la lai lan lang lao le lei leng li lia lian liang liao
            lie lin ling liu lo long lou lu lv luan lue lun luo ma mai man mang mao me
            mei men meng mi mian miao mie min ming miu mou mu na nai nan nang nao ne
            nei nen neng ng ni nian niang niao nie nin ning niu nong nou nu nv nuan nue
            nuo o ou pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou
            pu qi qia qian qiang qiao qie qin qing qiong qiu qu quan que qun ran rang
            rao re ren reng ri rong rou ru rua ruan rui run ruo sa sai san sang sao se
            sen seng sha shai shan shang shao she shei shen sheng shi shou shu shua
            shuai shuan shuang shui shun shuo si song sou su suan sui sun suo ta tai
            tan tang tao te teng ti tian tiao tie ting tong tou tu tuan tui tun tuo wa
            wai wan wang wei wen weng wo wu xi xia xian xiang xiao xie xin xing xiong
            xiu xu xuan xue xun ya yan yang yao ye yi yin ying yo yong you yu yuan yue
            yun za zai zan zang zao ze zei zen zeng zha zhai zhan zhang zhao zhe zhei
            zhen zheng zhi zhong zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi
            zong zou zu zuan zui zun zuo
            """;

    private static final Set<String> SYLLABLES;
    private static final Set<String> INCOMPLETE_SYLLABLES;

    static {
        Set<String> syllables = new HashSet<>();
        for (String token : SYLLABLE_TEXT.split("\\s+")) {
            if (!token.isEmpty()) syllables.add(token);
        }
        SYLLABLES = Set.copyOf(syllables);
        Set<String> prefixes = new HashSet<>();
        for (String syllable : SYLLABLES) {
            for (int length = 1; length < syllable.length(); length++) {
                prefixes.add(syllable.substring(0, length));
            }
        }
        prefixes.removeAll(SYLLABLES);
        INCOMPLETE_SYLLABLES = Set.copyOf(prefixes);
    }

    private record Entry(String key, String initials, String text, String displayPinyin,
                         int frequency) {
        Entry(String key, String text, String displayPinyin, int frequency) {
            this(key, initialsOf(displayPinyin), text, displayPinyin, frequency);
        }
    }

    private record Scored(Entry entry, int score) {}
}
