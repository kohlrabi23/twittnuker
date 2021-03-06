/*
 * Twittnuker - Twitter client for Android
 *
 * Copyright (C) 2013-2015 vanita5 <mail@vanita5.de>
 *
 * This program incorporates a modified version of Twidere.
 * Copyright (C) 2012-2015 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.vanita5.twittnuker.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import de.vanita5.twittnuker.R;
import de.vanita5.twittnuker.activity.SettingsActivity;
import de.vanita5.twittnuker.constant.IntentConstants;
import de.vanita5.twittnuker.fragment.SettingsDetailsFragment;

public class SecretCodeBroadcastReceiver extends BroadcastReceiver implements IntentConstants {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final Intent testIntent = new Intent(context, SettingsActivity.class);
        final String cls = SettingsDetailsFragment.class.getName();
        final Bundle args = new Bundle();
        args.putInt(EXTRA_RESID, R.xml.settings_hidden);
        args.putString(EXTRA_SETTINGS_INTENT_ACTION, INTENT_ACTION_HIDDEN_SETTINGS_ENTRY);
        testIntent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT, cls);
        testIntent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
        testIntent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT_TITLE, R.string.hidden_settings);
        testIntent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT_SHORT_TITLE, R.string.hidden_settings);
        testIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(testIntent);
    }

}