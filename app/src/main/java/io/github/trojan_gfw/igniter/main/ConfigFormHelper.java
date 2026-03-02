package io.github.trojan_gfw.igniter.main;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import java.util.function.Consumer;

/**
 * Helper class for managing configuration form fields in MainActivity.
 * Provides utility methods for creating text watchers and handling form input.
 */
public class ConfigFormHelper {

    /**
     * Creates a TextWatcher that calls the provided callback when text changes,
     * but only if the EditText has focus (to prevent infinite loops from programmatic updates).
     *
     * @param editText      The EditText to monitor
     * @param onTextChanged Callback invoked with the new text value when the user edits
     * @return A TextWatcher that can be added to the EditText
     */
    public static TextWatcher createConfigWatcher(EditText editText, Consumer<String> onTextChanged) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not used
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Not used
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (editText.hasFocus()) {
                    onTextChanged.accept(s.toString());
                }
            }
        };
    }

    /**
     * Creates a TextWatcher that trims the text and calls the provided callback.
     *
     * @param editText      The EditText to monitor
     * @param onTextChanged Callback invoked with the trimmed text value when the user edits
     * @return A TextWatcher that can be added to the EditText
     */
    public static TextWatcher createTrimmedConfigWatcher(EditText editText, Consumer<String> onTextChanged) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not used
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Not used
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (editText.hasFocus()) {
                    onTextChanged.accept(s.toString().trim());
                }
            }
        };
    }

    /**
     * Creates a TextWatcher for port number input that parses the text as an integer.
     *
     * @param editText     The EditText to monitor
     * @param onPortChanged Callback invoked with the parsed port number when the user edits
     * @return A TextWatcher that can be added to the EditText
     */
    public static TextWatcher createPortWatcher(EditText editText, Consumer<Integer> onPortChanged) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not used
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Not used
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (editText.hasFocus()) {
                    try {
                        int port = Integer.parseInt(s.toString());
                        onPortChanged.accept(port);
                    } catch (NumberFormatException e) {
                        // Ignore invalid port input
                    }
                }
            }
        };
    }
}
