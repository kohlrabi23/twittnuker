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

package de.vanita5.twittnuker.preference;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.preference.DialogPreference;
import android.util.AttributeSet;

import de.vanita5.twittnuker.R;
import de.vanita5.twittnuker.activity.support.DataExportActivity;
import de.vanita5.twittnuker.activity.support.DataImportActivity;

public class SettingsImportExportPreference extends DialogPreference {
	public SettingsImportExportPreference(Context context) {
		this(context, null);
	}

	public SettingsImportExportPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
        setDialogTitle(null);
		setPositiveButtonText(null);
		setNegativeButtonText(null);
	}

	@Override
	protected void onPrepareDialogBuilder(Builder builder) {
		final Context context = getContext();
		final String[] entries = new String[2];
		final Intent[] values = new Intent[2];
		entries[0] = context.getString(R.string.import_settings);
		entries[1] = context.getString(R.string.export_settings);
		values[0] = new Intent(context, DataImportActivity.class);
		values[1] = new Intent(context, DataExportActivity.class);
		builder.setItems(entries, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				context.startActivity(values[which]);
			}
		});
	}
}