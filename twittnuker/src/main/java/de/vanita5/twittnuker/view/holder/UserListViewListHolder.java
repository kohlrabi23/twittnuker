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

package de.vanita5.twittnuker.view.holder;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import de.vanita5.twittnuker.R;
import de.vanita5.twittnuker.view.iface.IColorLabelView;

public class UserListViewListHolder extends ViewListHolder {

    public final IColorLabelView content;
	public final ImageView profile_image;
	public final TextView name, description, created_by, members_count, subscribers_count;
    public int position;
	private float text_size;

	public UserListViewListHolder(final View view) {
		super(view);
        content = (IColorLabelView) view.findViewById(R.id.content);
		profile_image = (ImageView) findViewById(R.id.profile_image);
		name = (TextView) findViewById(R.id.name);
		description = (TextView) findViewById(R.id.description);
		created_by = (TextView) findViewById(R.id.created_by);
		members_count = (TextView) findViewById(R.id.members_count);
		subscribers_count = (TextView) findViewById(R.id.subscribers_count);
	}

	public void setTextSize(final float text_size) {
		if (this.text_size == text_size) return;
		this.text_size = text_size;
        if (description != null) {
		    description.setTextSize(text_size);
        }
		name.setTextSize(text_size * 1.05f);
		created_by.setTextSize(text_size * 0.65f);
	}

}
