/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package cpc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parser for ImageJ macro options passed to the CPC plugin command.
 */
public final class CPCMacroOptionsParser {

    private CPCMacroOptionsParser() {
    }

    public static CPCMacroOptions parse(String optionsText) {
        CPCMacroOptions options = new CPCMacroOptions();
        Set<String> seenKeys = new HashSet<String>();
        List<String> tokens = tokenize(optionsText == null ? "" : optionsText);
        for (String token : tokens) {
            int eq = token.indexOf('=');
            if (eq >= 0) {
                String key = token.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String value = decodeValue(token.substring(eq + 1).trim());
                if (!seenKeys.add(key)) {
                    throw new IllegalArgumentException("Duplicate macro option: " + key);
                }
                applyKeyValue(options, key, value);
            } else {
                applyFlag(options, token.toLowerCase(Locale.ROOT));
            }
        }
        options.validate();
        return options;
    }

    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<String>();
        StringBuilder token = new StringBuilder();
        boolean inBracket = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inBracket) {
                if (c == '[') {
                    throw new IllegalArgumentException("Nested brackets are not allowed in macro options.");
                }
                if (c == '\n' || c == '\r') {
                    throw new IllegalArgumentException("Line breaks are not allowed in macro option values.");
                }
                token.append(c);
                if (c == ']') inBracket = false;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (token.length() > 0) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
                continue;
            }
            if (c == '[') {
                inBracket = true;
            } else if (c == ']') {
                throw new IllegalArgumentException("Unexpected closing bracket in macro options.");
            }
            token.append(c);
        }
        if (inBracket) {
            throw new IllegalArgumentException("Unclosed bracketed macro option value.");
        }
        if (token.length() > 0) tokens.add(token.toString());
        return tokens;
    }

    private static void applyKeyValue(CPCMacroOptions options, String key, String value) {
        if ("mode".equals(key)) {
            String v = value.toLowerCase(Locale.ROOT);
            if ("labels".equals(v) || "label".equals(v) || "images".equals(v)) {
                options.setMode(CPCMacroOptions.InputMode.LABELS);
            } else if ("rois".equals(v) || "roi".equals(v) || "roi_sets".equals(v)) {
                options.setMode(CPCMacroOptions.InputMode.ROIS);
            } else {
                throw new IllegalArgumentException("mode must be labels or rois.");
            }
            return;
        }
        if ("reference".equals(key)) {
            options.setReferenceTitle(value);
            return;
        }
        if ("reference_path".equals(key)) {
            options.setReferencePath(value);
            return;
        }
        if ("save_dir".equals(key)) {
            options.setSaveDir(value);
            return;
        }

        int imagePathIndex = slotIndex(key, "image", "_path");
        if (imagePathIndex >= 0) {
            options.setImagePath(imagePathIndex, value);
            return;
        }
        int imageIndex = slotIndex(key, "image", "");
        if (imageIndex >= 0) {
            options.setImageTitle(imageIndex, value);
            return;
        }
        int rawPathIndex = slotIndex(key, "raw", "_path");
        if (rawPathIndex >= 0) {
            options.setRawPath(rawPathIndex, value);
            return;
        }
        int rawIndex = slotIndex(key, "raw", "");
        if (rawIndex >= 0) {
            options.setRawTitle(rawIndex, value);
            return;
        }
        int roiIndex = slotIndex(key, "roi", "");
        if (roiIndex >= 0) {
            options.setRoiPath(roiIndex, value);
            return;
        }

        throw new IllegalArgumentException("Unknown CPC macro option: " + key);
    }

    private static void applyFlag(CPCMacroOptions options, String flag) {
        if ("labels".equals(flag)) {
            options.setMode(CPCMacroOptions.InputMode.LABELS);
        } else if ("rois".equals(flag) || "roi_sets".equals(flag)) {
            options.setMode(CPCMacroOptions.InputMode.ROIS);
        } else if ("bidirectional".equals(flag)) {
            options.setBidirectional(true);
        } else if ("unidirectional".equals(flag) || "no_bidirectional".equals(flag)) {
            options.setBidirectional(false);
        } else if ("center_of_mass".equals(flag) || "centre_of_mass".equals(flag)
                || "com".equals(flag) || "intensity_weighted".equals(flag)) {
            options.setCenterOfMass(true);
        } else if ("objects".equals(flag) || "per_object".equals(flag)) {
            options.setPerObjectTables(true);
        } else if ("hide_objects".equals(flag) || "no_objects".equals(flag)) {
            options.setPerObjectTables(false);
        } else if ("summary".equals(flag)) {
            options.setSummaryTable(true);
        } else if ("hide_summary".equals(flag) || "no_summary".equals(flag)) {
            options.setSummaryTable(false);
        } else if ("extended".equals(flag) || "extended_data".equals(flag)) {
            options.setExtendedData(true);
        } else if ("multi_target".equals(flag) || "multi".equals(flag)) {
            options.setMultiTarget(true);
        } else if ("centroid_maps".equals(flag) || "maps".equals(flag)) {
            options.setCentroidMaps(true);
        } else if ("auto_save".equals(flag) || "autosave".equals(flag)) {
            options.setAutoSave(true);
        } else if ("hide_display".equals(flag) || "no_display".equals(flag)) {
            options.setHideDisplay(true);
        } else {
            throw new IllegalArgumentException("Unknown CPC macro flag: " + flag);
        }
    }

    private static int slotIndex(String key, String prefix, String suffix) {
        if (!key.startsWith(prefix) || !key.endsWith(suffix)) return -1;
        String middle = key.substring(prefix.length(), key.length() - suffix.length());
        if (middle.length() != 1) return -1;
        char c = middle.charAt(0);
        if (c < '1' || c > '5') return -1;
        return c - '1';
    }

    private static String decodeValue(String raw) {
        if (raw.length() >= 2 && raw.charAt(0) == '[' && raw.charAt(raw.length() - 1) == ']') {
            String inner = raw.substring(1, raw.length() - 1);
            if (inner.indexOf('[') >= 0 || inner.indexOf(']') >= 0
                    || inner.indexOf('"') >= 0 || inner.indexOf('\\') >= 0
                    || inner.indexOf('\n') >= 0 || inner.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Bracketed macro values must not contain brackets, quotes, backslashes, or line breaks.");
            }
            return inner;
        }
        if (raw.indexOf('"') >= 0 || raw.indexOf('\\') >= 0
                || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Macro values must not contain quotes, backslashes, or line breaks.");
        }
        return raw;
    }
}
