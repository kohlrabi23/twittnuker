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

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;

public class ClearCachePreference extends AsyncTaskPreference {

	public ClearCachePreference(final Context context) {
		this(context, null);
	}

	public ClearCachePreference(final Context context, final AttributeSet attrs) {
		this(context, attrs, android.R.attr.preferenceStyle);
	}

	public ClearCachePreference(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected void doInBackground() {
		final Context context = getContext();
		if (context == null) return;
        final File externalCacheDir = context.getExternalCacheDir();
        if (externalCacheDir != null) {
            for (final File file : externalCacheDir.listFiles((FileFilter) null)) {
				deleteRecursive(file);
			}
		}
        final File internalCacheDir = context.getCacheDir();
        if (internalCacheDir != null) {
            for (final File file : internalCacheDir.listFiles((FileFilter) null)) {
				deleteRecursive(file);
			}
		}
	}

	private static void deleteRecursive(final File f) {
		if (f.isDirectory()) {
            final File[] files = f.listFiles();
            if (files == null) return;
            for (final File c : files) {
				deleteRecursive(c);
			}
		}
        if (!f.delete()) {
            Log.w(LOGTAG, String.format("Unable to delete %s", f));
        }
	}

}