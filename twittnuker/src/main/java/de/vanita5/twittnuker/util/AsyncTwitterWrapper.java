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

package de.vanita5.twittnuker.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.util.LongSparseArray;
import android.util.Log;

import com.squareup.otto.Bus;

import org.apache.commons.lang3.ArrayUtils;
import org.mariotaku.querybuilder.Columns.Column;
import org.mariotaku.querybuilder.Expression;
import org.mariotaku.querybuilder.RawItemArray;
import org.mariotaku.querybuilder.SQLFunctions;
import de.vanita5.twittnuker.R;
import de.vanita5.twittnuker.app.TwittnukerApplication;
import de.vanita5.twittnuker.model.ListResponse;
import de.vanita5.twittnuker.model.ParcelableAccount;
import de.vanita5.twittnuker.model.ParcelableLocation;
import de.vanita5.twittnuker.model.ParcelableMediaUpdate;
import de.vanita5.twittnuker.model.ParcelableStatus;
import de.vanita5.twittnuker.model.ParcelableStatusUpdate;
import de.vanita5.twittnuker.model.ParcelableUser;
import de.vanita5.twittnuker.model.ParcelableUserList;
import de.vanita5.twittnuker.model.SingleResponse;
import de.vanita5.twittnuker.preference.HomeRefreshContentPreference;
import de.vanita5.twittnuker.provider.TwidereDataStore;
import de.vanita5.twittnuker.provider.TwidereDataStore.CachedHashtags;
import de.vanita5.twittnuker.provider.TwidereDataStore.CachedTrends;
import de.vanita5.twittnuker.provider.TwidereDataStore.DirectMessages;
import de.vanita5.twittnuker.provider.TwidereDataStore.DirectMessages.Inbox;
import de.vanita5.twittnuker.provider.TwidereDataStore.DirectMessages.Outbox;
import de.vanita5.twittnuker.provider.TwidereDataStore.Drafts;
import de.vanita5.twittnuker.provider.TwidereDataStore.Mentions;
import de.vanita5.twittnuker.provider.TwidereDataStore.SavedSearches;
import de.vanita5.twittnuker.provider.TwidereDataStore.Statuses;
import de.vanita5.twittnuker.service.BackgroundOperationService;
import de.vanita5.twittnuker.task.ManagedAsyncTask;
import de.vanita5.twittnuker.util.collection.LongSparseMap;
import de.vanita5.twittnuker.util.content.ContentResolverUtils;
import de.vanita5.twittnuker.util.message.FavoriteCreatedEvent;
import de.vanita5.twittnuker.util.message.FavoriteDestroyedEvent;
import de.vanita5.twittnuker.util.message.FriendshipUpdatedEvent;
import de.vanita5.twittnuker.util.message.GetMessagesTaskEvent;
import de.vanita5.twittnuker.util.message.GetStatusesTaskEvent;
import de.vanita5.twittnuker.util.message.ProfileUpdatedEvent;
import de.vanita5.twittnuker.util.message.StatusDestroyedEvent;
import de.vanita5.twittnuker.util.message.StatusListChangedEvent;
import de.vanita5.twittnuker.util.message.StatusRetweetedEvent;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import twitter4j.DirectMessage;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.SavedSearch;
import twitter4j.Trends;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserList;
import twitter4j.http.HttpResponseCode;

import static de.vanita5.twittnuker.provider.TwidereDataStore.STATUSES_URIS;
import static de.vanita5.twittnuker.util.ContentValuesCreator.createDirectMessage;
import static de.vanita5.twittnuker.util.ContentValuesCreator.createStatus;
import static de.vanita5.twittnuker.util.ContentValuesCreator.createTrends;
import static de.vanita5.twittnuker.util.Utils.getActivatedAccountIds;
import static de.vanita5.twittnuker.util.Utils.getDefaultAccountId;
import static de.vanita5.twittnuker.util.Utils.getNewestMessageIdsFromDatabase;
import static de.vanita5.twittnuker.util.Utils.getNewestStatusIdsFromDatabase;
import static de.vanita5.twittnuker.util.Utils.getStatusCountInDatabase;
import static de.vanita5.twittnuker.util.Utils.getTwitterInstance;
import static de.vanita5.twittnuker.util.Utils.getUserName;
import static de.vanita5.twittnuker.util.Utils.truncateMessages;
import static de.vanita5.twittnuker.util.Utils.truncateStatuses;
import static de.vanita5.twittnuker.util.content.ContentResolverUtils.bulkDelete;
import static de.vanita5.twittnuker.util.content.ContentResolverUtils.bulkInsert;

public class AsyncTwitterWrapper extends TwitterWrapper {

	private static AsyncTwitterWrapper sInstance;

	private final Context mContext;
	private final AsyncTaskManager mAsyncTaskManager;
	private final SharedPreferences mPreferences;
	private final MessagesManager mMessagesManager;
	private final ContentResolver mResolver;

	private int mGetHomeTimelineTaskId, mGetMentionsTaskId;
	private int mGetReceivedDirectMessagesTaskId, mGetSentDirectMessagesTaskId;
	private int mGetLocalTrendsTaskId;

    private LongSparseMap<Long> mCreatingFavoriteIds = new LongSparseMap<>();
    private LongSparseMap<Long> mDestroyingFavoriteIds = new LongSparseMap<>();
    private LongSparseMap<Long> mCreatingRetweetIds = new LongSparseMap<>();
    private LongSparseMap<Long> mDestroyingStatusIds = new LongSparseMap<>();

    private CopyOnWriteArraySet<Long> mSendingDraftIds = new CopyOnWriteArraySet<>();

	public AsyncTwitterWrapper(final Context context) {
		mContext = context;
		final TwittnukerApplication app = TwittnukerApplication.getInstance(context);
		mAsyncTaskManager = app.getAsyncTaskManager();
		mMessagesManager = app.getMessagesManager();
		mPreferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mResolver = context.getContentResolver();
	}

	public int acceptFriendshipAsync(final long accountId, final long userId) {
		final AcceptFriendshipTask task = new AcceptFriendshipTask(accountId, userId);
		return mAsyncTaskManager.add(task, true);
	}

    public void addSendingDraftId(long id) {
        mSendingDraftIds.add(id);
        mResolver.notifyChange(Drafts.CONTENT_URI_UNSENT, null);
    }

	public int addUserListMembersAsync(final long accountId, final long listId, final ParcelableUser... users) {
		final AddUserListMembersTask task = new AddUserListMembersTask(accountId, listId, users);
		return mAsyncTaskManager.add(task, true);
	}

    public int cancelRetweetAsync(long account_id, long status_id, long my_retweet_id) {
        if (my_retweet_id > 0)
            return destroyStatusAsync(account_id, my_retweet_id);
        else if (status_id > 0)
            return destroyStatusAsync(account_id, status_id);
        return -1;
    }

    public int cancelRetweetAsync(@NonNull final ParcelableStatus status) {
        return cancelRetweetAsync(status.account_id, status.id, status.my_retweet_id);
    }

	public void clearNotificationAsync(final int notificationType) {
		clearNotificationAsync(notificationType, 0);
	}

	public void clearNotificationAsync(final int notificationId, final long notificationAccount) {
		final ClearNotificationTask task = new ClearNotificationTask(notificationId, notificationAccount);
        AsyncTaskUtils.executeTask(task);
	}

	public void clearUnreadCountAsync(final int position) {
		final ClearUnreadCountTask task = new ClearUnreadCountTask(position);
        AsyncTaskUtils.executeTask(task);
	}

	public int createBlockAsync(final long accountId, final long user_id) {
		final CreateBlockTask task = new CreateBlockTask(accountId, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int createFavoriteAsync(final long accountId, final long status_id) {
		final CreateFavoriteTask task = new CreateFavoriteTask(accountId, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int createFriendshipAsync(final long accountId, final long userId) {
		final CreateFriendshipTask task = new CreateFriendshipTask(accountId, userId);
		return mAsyncTaskManager.add(task, true);
	}

	public int createMultiBlockAsync(final long accountId, final long[] userIds) {
		final CreateMultiBlockTask task = new CreateMultiBlockTask(accountId, userIds);
		return mAsyncTaskManager.add(task, true);
	}

    public int createMuteAsync(final long accountId, final long user_id) {
        final CreateMuteTask task = new CreateMuteTask(accountId, user_id);
        return mAsyncTaskManager.add(task, true);
    }

	public int createSavedSearchAsync(final long accountId, final String query) {
		final CreateSavedSearchTask task = new CreateSavedSearchTask(accountId, query);
		return mAsyncTaskManager.add(task, true);
	}

	public int createUserListAsync(final long accountId, final String list_name, final boolean is_public,
			final String description) {
		final CreateUserListTask task = new CreateUserListTask(accountId, list_name, is_public, description);
		return mAsyncTaskManager.add(task, true);
	}

	public int createUserListSubscriptionAsync(final long accountId, final long listId) {
		final CreateUserListSubscriptionTask task = new CreateUserListSubscriptionTask(accountId, listId);
		return mAsyncTaskManager.add(task, true);
	}

	public int deleteUserListMembersAsync(final long accountId, final long listId, final ParcelableUser... users) {
		final DeleteUserListMembersTask task = new DeleteUserListMembersTask(accountId, listId, users);
		return mAsyncTaskManager.add(task, true);
	}

	public int denyFriendshipAsync(final long accountId, final long userId) {
		final DenyFriendshipTask task = new DenyFriendshipTask(accountId, userId);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyBlockAsync(final long accountId, final long user_id) {
		final DestroyBlockTask task = new DestroyBlockTask(accountId, user_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyDirectMessageAsync(final long accountId, final long message_id) {
		final DestroyDirectMessageTask task = new DestroyDirectMessageTask(accountId, message_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyFavoriteAsync(final long accountId, final long status_id) {
		final DestroyFavoriteTask task = new DestroyFavoriteTask(accountId, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyFriendshipAsync(final long accountId, final long user_id) {
		final DestroyFriendshipTask task = new DestroyFriendshipTask(accountId, user_id);
		return mAsyncTaskManager.add(task, true);
	}

    public int destroyMuteAsync(final long accountId, final long user_id) {
        final DestroyMuteTask task = new DestroyMuteTask(accountId, user_id);
        return mAsyncTaskManager.add(task, true);
    }

	public int destroySavedSearchAsync(final long accountId, final int searchId) {
		final DestroySavedSearchTask task = new DestroySavedSearchTask(accountId, searchId);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyStatusAsync(final long accountId, final long status_id) {
		final DestroyStatusTask task = new DestroyStatusTask(accountId, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyUserListAsync(final long accountId, final long listId) {
		final DestroyUserListTask task = new DestroyUserListTask(accountId, listId);
		return mAsyncTaskManager.add(task, true);
	}

	public int destroyUserListSubscriptionAsync(final long accountId, final long listId) {
		final DestroyUserListSubscriptionTask task = new DestroyUserListSubscriptionTask(accountId, listId);
		return mAsyncTaskManager.add(task, true);
	}

    public Context getContext() {
        return mContext;
    }

	public int getHomeTimelineAsync(final long[] accountIds, final long[] max_ids, final long[] since_ids) {
		mAsyncTaskManager.cancel(mGetHomeTimelineTaskId);
		final GetHomeTimelineTask task = new GetHomeTimelineTask(accountIds, max_ids, since_ids);
		return mGetHomeTimelineTaskId = mAsyncTaskManager.add(task, true);
	}

    public static AsyncTwitterWrapper getInstance(final Context context) {
        if (sInstance != null) return sInstance;
        return sInstance = new AsyncTwitterWrapper(context);
    }

	public int getLocalTrendsAsync(final long accountId, final int woeid) {
		mAsyncTaskManager.cancel(mGetLocalTrendsTaskId);
		final GetLocalTrendsTask task = new GetLocalTrendsTask(accountId, woeid);
		return mGetLocalTrendsTaskId = mAsyncTaskManager.add(task, true);
	}

    public int getMentionsTimelineAsync(final long[] accountIds, final long[] max_ids, final long[] since_ids) {
		mAsyncTaskManager.cancel(mGetMentionsTaskId);
		final GetMentionsTask task = new GetMentionsTask(accountIds, max_ids, since_ids);
		return mGetMentionsTaskId = mAsyncTaskManager.add(task, true);
	}

	public int getReceivedDirectMessagesAsync(final long[] accountIds, final long[] max_ids, final long[] since_ids) {
		mAsyncTaskManager.cancel(mGetReceivedDirectMessagesTaskId);
		final GetReceivedDirectMessagesTask task = new GetReceivedDirectMessagesTask(accountIds, max_ids, since_ids);
		return mGetReceivedDirectMessagesTaskId = mAsyncTaskManager.add(task, true);
	}

    public int getSavedSearchesAsync(long[] accountIds) {
        final GetSavedSearchesTask task = new GetSavedSearchesTask(this);
        final Long[] ids = new Long[accountIds.length];
        for (int i = 0, j = accountIds.length; i < j; i++) {
            ids[i] = accountIds[i];
        }
        return mAsyncTaskManager.add(task, true, ids);
    }

    @NonNull
    public long[] getSendingDraftIds() {
        return ArrayUtils.toPrimitive(mSendingDraftIds.toArray(new Long[mSendingDraftIds.size()]));
    }

	public int getSentDirectMessagesAsync(final long[] accountIds, final long[] max_ids, final long[] since_ids) {
		mAsyncTaskManager.cancel(mGetSentDirectMessagesTaskId);
		final GetSentDirectMessagesTask task = new GetSentDirectMessagesTask(accountIds, max_ids, since_ids);
		return mGetSentDirectMessagesTaskId = mAsyncTaskManager.add(task, true);
	}

    public AsyncTaskManager getTaskManager() {
        return mAsyncTaskManager;
    }

	public boolean hasActivatedTask() {
		return mAsyncTaskManager.hasRunningTask();
	}

    public boolean isCreatingFavorite(final long accountId, final long statusId) {
        return mCreatingFavoriteIds.has(accountId, statusId);
    }

    public boolean isCreatingFriendship(final long accountId, final long userId) {
		for (final ManagedAsyncTask<?, ?, ?> task : mAsyncTaskManager.getTaskSpecList()) {
			if (task instanceof CreateFriendshipTask) {
                final CreateFriendshipTask createFriendshipTask = (CreateFriendshipTask) task;
                if (createFriendshipTask.getStatus() == AsyncTask.Status.RUNNING
                        && createFriendshipTask.getAccountId() == accountId
                        && createFriendshipTask.getUserId() == userId)
					return true;
			}
		}
		return false;
	}

    public boolean isCreatingRetweet(final long accountId, final long statusId) {
        return mCreatingRetweetIds.has(accountId, statusId);
    }

    public boolean isDestroyingFavorite(final long accountId, final long statusId) {
        return mDestroyingFavoriteIds.has(accountId, statusId);
    }

    public boolean isDestroyingFriendship(final long accountId, final long userId) {
		for (final ManagedAsyncTask<?, ?, ?> task : mAsyncTaskManager.getTaskSpecList()) {
			if (task instanceof DestroyFriendshipTask) {
                final DestroyFriendshipTask destroyFriendshipTask = (DestroyFriendshipTask) task;
                if (destroyFriendshipTask.getStatus() == AsyncTask.Status.RUNNING
                        && destroyFriendshipTask.getAccountId() == accountId
                        && destroyFriendshipTask.getUserId() == userId)
					return true;
			}
		}
		return false;
	}

    public boolean isDestroyingStatus(final long accountId, final long statusId) {
        return mDestroyingStatusIds.has(accountId, statusId);
    }

	public boolean isHomeTimelineRefreshing() {
        return mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_GET_HOME_TIMELINE);
	}

	public boolean isLocalTrendsRefreshing() {
		return mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_GET_TRENDS)
				|| mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_STORE_TRENDS);
	}

    public boolean isMentionsTimelineRefreshing() {
        return mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_GET_MENTIONS);
	}

	public boolean isReceivedDirectMessagesRefreshing() {
        return mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_GET_RECEIVED_DIRECT_MESSAGES);
	}

	public boolean isSentDirectMessagesRefreshing() {
        return mAsyncTaskManager.hasRunningTasksForTag(TASK_TAG_GET_SENT_DIRECT_MESSAGES);
	}

	public int refreshAll() {
		final long[] accountIds = getActivatedAccountIds(mContext);
		return refreshAll(accountIds);
	}

	public int refreshAll(final long[] accountIds) {
		if (mPreferences.getBoolean(KEY_HOME_REFRESH_MENTIONS, HomeRefreshContentPreference.DEFAULT_ENABLE_MENTIONS)) {
			final long[] sinceIds = getNewestStatusIdsFromDatabase(mContext, Mentions.CONTENT_URI, accountIds);
            getMentionsTimelineAsync(accountIds, null, sinceIds);
		}
		if (mPreferences.getBoolean(KEY_HOME_REFRESH_DIRECT_MESSAGES,
				HomeRefreshContentPreference.DEFAULT_ENABLE_DIRECT_MESSAGES)) {
			final long[] sinceIds = getNewestMessageIdsFromDatabase(mContext, DirectMessages.Inbox.CONTENT_URI,
					accountIds);
			getReceivedDirectMessagesAsync(accountIds, null, sinceIds);
			getSentDirectMessagesAsync(accountIds, null, null);
		}
		if (mPreferences.getBoolean(KEY_HOME_REFRESH_TRENDS, HomeRefreshContentPreference.DEFAULT_ENABLE_TRENDS)) {
			final long accountId = getDefaultAccountId(mContext);
			final int woeId = mPreferences.getInt(KEY_LOCAL_TRENDS_WOEID, 1);
			getLocalTrendsAsync(accountId, woeId);
		}
        getSavedSearchesAsync(accountIds);
		final long[] statusSinceIds = getNewestStatusIdsFromDatabase(mContext, Statuses.CONTENT_URI, accountIds);
		return getHomeTimelineAsync(accountIds, null, statusSinceIds);
	}

    public void removeSendingDraftId(long id) {
        mSendingDraftIds.remove(id);
        mResolver.notifyChange(Drafts.CONTENT_URI_UNSENT, null);
    }

    public void removeUnreadCountsAsync(final int position, final LongSparseArray<Set<Long>> counts) {
		final RemoveUnreadCountsTask task = new RemoveUnreadCountsTask(position, counts);
        AsyncTaskUtils.executeTask(task);
	}

	public int reportMultiSpam(final long accountId, final long[] user_ids) {
		final ReportMultiSpamTask task = new ReportMultiSpamTask(accountId, user_ids);
		return mAsyncTaskManager.add(task, true);
	}

	public int reportSpamAsync(final long accountId, final long user_id) {
		final ReportSpamTask task = new ReportSpamTask(accountId, user_id);
		return mAsyncTaskManager.add(task, true);
	}

    public int retweetStatusAsync(final long accountId, final long status_id) {
		final RetweetStatusTask task = new RetweetStatusTask(accountId, status_id);
		return mAsyncTaskManager.add(task, true);
	}

	public int sendDirectMessageAsync(final long accountId, final long recipientId, final String text,
			final String imageUri) {
		final Intent intent = new Intent(mContext, BackgroundOperationService.class);
		intent.setAction(INTENT_ACTION_SEND_DIRECT_MESSAGE);
		intent.putExtra(EXTRA_ACCOUNT_ID, accountId);
		intent.putExtra(EXTRA_RECIPIENT_ID, recipientId);
		intent.putExtra(EXTRA_TEXT, text);
		intent.putExtra(EXTRA_IMAGE_URI, imageUri);
		mContext.startService(intent);
		return 0;
	}

	public int updateProfile(final long accountId, final String name, final String url, final String location,
			final String description) {
		final UpdateProfileTask task = new UpdateProfileTask(mContext, mAsyncTaskManager, accountId, name, url,
				location, description);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateProfileBannerImage(final long accountId, final Uri image_uri, final boolean delete_image) {
		final UpdateProfileBannerImageTask task = new UpdateProfileBannerImageTask(mContext, mAsyncTaskManager,
				accountId, image_uri, delete_image);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateProfileImage(final long accountId, final Uri imageUri, final boolean deleteImage) {
		final UpdateProfileImageTask task = new UpdateProfileImageTask(mContext, mAsyncTaskManager, accountId,
				imageUri, deleteImage);
		return mAsyncTaskManager.add(task, true);
	}

	public int updateStatusAsync(final long[] accountIds, final String text, final ParcelableLocation location,
                                 final ParcelableMediaUpdate[] media, final long inReplyToStatusId,
                                 final boolean isPossiblySensitive) {
		final ParcelableStatusUpdate.Builder builder = new ParcelableStatusUpdate.Builder();
        builder.accounts(ParcelableAccount.getAccounts(mContext, accountIds));
		builder.text(text);
		builder.location(location);
        builder.media(media);
		builder.inReplyToStatusId(inReplyToStatusId);
		builder.isPossiblySensitive(isPossiblySensitive);
		return updateStatusesAsync(builder.build());
	}

	public int updateStatusesAsync(final ParcelableStatusUpdate... statuses) {
		final Intent intent = new Intent(mContext, BackgroundOperationService.class);
		intent.setAction(INTENT_ACTION_UPDATE_STATUS);
		intent.putExtra(EXTRA_STATUSES, statuses);
		mContext.startService(intent);
		return 0;
	}

	public int updateUserListDetails(final long accountId, final long listId, final boolean isPublic,
			final String name, final String description) {
		final UpdateUserListDetailsTask task = new UpdateUserListDetailsTask(accountId, listId, isPublic, name,
				description);
		return mAsyncTaskManager.add(task, true);
	}

    static class GetSavedSearchesTask extends ManagedAsyncTask<Long, Void, SingleResponse<Void>> {

        private final Context mContext;

        GetSavedSearchesTask(AsyncTwitterWrapper twitter) {
            super(twitter.getContext(), twitter.getTaskManager());
            this.mContext = twitter.getContext();
	    }

        @Override
        protected SingleResponse<Void> doInBackground(Long... params) {
            final ContentResolver cr = mContext.getContentResolver();
            for (long accountId : params) {
                final Twitter twitter = Utils.getTwitterInstance(mContext, accountId, true);
                try {
                    final ResponseList<SavedSearch> searches = twitter.getSavedSearches();
                    final ContentValues[] values = ContentValuesCreator.createSavedSearches(searches, accountId);
                    final Expression where = Expression.equals(SavedSearches.ACCOUNT_ID, accountId);
                    cr.delete(SavedSearches.CONTENT_URI, where.getSQL(), null);
                    ContentResolverUtils.bulkInsert(cr, SavedSearches.CONTENT_URI, values);
                } catch (TwitterException e) {
                    Log.w(LOGTAG, e);
                }
            }
            return SingleResponse.getInstance();
        }
    }

    public static class UpdateProfileBannerImageTask extends ManagedAsyncTask<Object, Void, SingleResponse<ParcelableUser>> {

		private final long mAccountId;
		private final Uri mImageUri;
		private final boolean mDeleteImage;
		private final Context mContext;

		public UpdateProfileBannerImageTask(final Context context, final AsyncTaskManager manager,
				final long account_id, final Uri image_uri, final boolean delete_image) {
			super(context, manager);
			mContext = context;
			mAccountId = account_id;
			mImageUri = image_uri;
			mDeleteImage = delete_image;
		}

		@Override
        protected SingleResponse<ParcelableUser> doInBackground(final Object... params) {
            try {
                final Twitter twitter = getTwitterInstance(mContext, mAccountId, true);
                TwitterWrapper.updateProfileBannerImage(mContext, twitter, mImageUri, mDeleteImage);
                // Wait for 5 seconds, see
                // https://dev.twitter.com/docs/api/1.1/post/account/update_profile_image
                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException e) {
                    Log.w(LOGTAG, e);
		        }
                final User user = TwitterWrapper.tryShowUser(twitter, mAccountId, null);
                return SingleResponse.getInstance(new ParcelableUser(user, mAccountId));
            } catch (TwitterException | FileNotFoundException e) {
                return SingleResponse.getInstance(e);
            }
        }

		@Override
        protected void onPostExecute(final SingleResponse<ParcelableUser> result) {
            super.onPostExecute(result);
            if (result.hasData()) {
				Utils.showOkMessage(mContext, R.string.profile_banner_image_updated, false);
                final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
                bus.post(new ProfileUpdatedEvent(result.getData()));
			} else {
				Utils.showErrorMessage(mContext, R.string.action_updating_profile_banner_image, result.getException(),
						true);
			}
		}

	}

	public static class UpdateProfileImageTask extends ManagedAsyncTask<Object, Void, SingleResponse<ParcelableUser>> {

        private final long mAccountId;
        private final Uri mImageUri;
        private final boolean mDeleteImage;
        private final Context mContext;

		public UpdateProfileImageTask(final Context context, final AsyncTaskManager manager, final long account_id,
				final Uri image_uri, final boolean delete_image) {
			super(context, manager);
            this.mContext = context;
            this.mAccountId = account_id;
            this.mImageUri = image_uri;
            this.mDeleteImage = delete_image;
		}

		@Override
		protected SingleResponse<ParcelableUser> doInBackground(final Object... params) {
            try {
                final Twitter twitter = getTwitterInstance(mContext, mAccountId, true);
                TwitterWrapper.updateProfileImage(mContext, twitter, mImageUri, mDeleteImage);
                // Wait for 5 seconds, see
                // https://dev.twitter.com/docs/api/1.1/post/account/update_profile_image
                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException e) {
                    Log.w(LOGTAG, e);
		        }
                final User user = TwitterWrapper.tryShowUser(twitter, mAccountId, null);
                return SingleResponse.getInstance(new ParcelableUser(user, mAccountId));
            } catch (TwitterException | FileNotFoundException e) {
                return SingleResponse.getInstance(e);
            }
        }

		@Override
		protected void onPostExecute(final SingleResponse<ParcelableUser> result) {
            super.onPostExecute(result);
			if (result.hasData()) {
                Utils.showOkMessage(mContext, R.string.profile_image_updated, false);
                final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
                bus.post(new ProfileUpdatedEvent(result.getData()));
			} else {
                Utils.showErrorMessage(mContext, R.string.action_updating_profile_image, result.getException(), true);
			}
		}

	}

	public static class UpdateProfileTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableUser>> {

		private final long account_id;
		private final String name, url, location, description;
		private final Context context;

		public UpdateProfileTask(final Context context, final AsyncTaskManager manager, final long account_id,
				final String name, final String url, final String location, final String description) {
			super(context, manager);
			this.context = context;
			this.account_id = account_id;
			this.name = name;
			this.url = url;
			this.location = location;
			this.description = description;
		}

		@Override
		protected SingleResponse<ParcelableUser> doInBackground(final Void... params) {
			return updateProfile(context, account_id, name, url, location, description);
		}

		@Override
		protected void onPostExecute(final SingleResponse<ParcelableUser> result) {
			if (result.hasData()) {
				Utils.showOkMessage(context, R.string.profile_updated, false);
                final Bus bus = TwittnukerApplication.getInstance(context).getMessageBus();
                bus.post(new ProfileUpdatedEvent(result.getData()));
			} else {
				Utils.showErrorMessage(context, context.getString(R.string.action_updating_profile),
						result.getException(), true);
			}
			super.onPostExecute(result);
		}

	}

	class AcceptFriendshipTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private final long mAccountId;
		private final long mUserId;

		public AcceptFriendshipTask(final long account_id, final long user_id) {
			super(mContext, mAsyncTaskManager);
			mAccountId = account_id;
			mUserId = user_id;
		}

		public long getAccountId() {
			return mAccountId;
		}

		public long getUserId() {
			return mUserId;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, mAccountId, false);
			if (twitter == null) return SingleResponse.getInstance();
			try {
				final User user = twitter.acceptFriendship(mUserId);
				return SingleResponse.getInstance(user, null);
			} catch (final TwitterException e) {
				return SingleResponse.getInstance(null, e);
			}
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result.hasData()) {
				final User user = result.getData();
				final String message = mContext.getString(R.string.accepted_users_follow_request,
						getUserName(user));
				mMessagesManager.showOkMessage(message, false);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_accepting_follow_request, result.getException(),
						false);
			}
			final Intent intent = new Intent(BROADCAST_FRIENDSHIP_ACCEPTED);
			intent.putExtra(EXTRA_USER_ID, mUserId);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

	class AddUserListMembersTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableUserList>> {

		private final long accountId;
		private final long listId;
		private final ParcelableUser[] users;

		public AddUserListMembersTask(final long accountId, final long listId, final ParcelableUser[] users) {
			super(mContext, mAsyncTaskManager);
			this.accountId = accountId;
			this.listId = listId;
			this.users = users;
		}

        @Override
        protected SingleResponse<ParcelableUserList> doInBackground(final Void... params) {
            final Twitter twitter = getTwitterInstance(mContext, accountId, false);
			if (twitter == null || users == null) return SingleResponse.getInstance();
            try {
                final long[] userIds = new long[users.length];
                for (int i = 0, j = users.length; i < j; i++) {
                    userIds[i] = users[i].id;
                }
                final ParcelableUserList list = new ParcelableUserList(twitter.addUserListMembers(listId, userIds),
						accountId);
				return SingleResponse.getInstance(list, null);
            } catch (final TwitterException e) {
				return SingleResponse.getInstance(null, e);
            }
        }

		@Override
		protected void onPostExecute(final SingleResponse<ParcelableUserList> result) {
			final boolean succeed = result.hasData() && result.getData().id > 0;
			if (succeed) {
				final String message;
				if (users.length == 1) {
					final ParcelableUser user = users[0];
                    final String displayName = UserColorNameUtils.getDisplayName(mContext, user.name, user.screen_name);
					message = mContext.getString(R.string.added_user_to_list, displayName, result.getData().name);
				} else {
					final Resources res = mContext.getResources();
					message = res.getQuantityString(R.plurals.added_N_users_to_list, users.length, users.length,
							result.getData().name);
				}
				mMessagesManager.showOkMessage(message, false);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_adding_member, result.getException(), true);
			}
			final Intent intent = new Intent(BROADCAST_USER_LIST_MEMBERS_ADDED);
			intent.putExtra(EXTRA_USER_LIST, result.getData());
			intent.putExtra(EXTRA_USERS, users);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

    final class ClearNotificationTask extends AsyncTask<Object, Void, Integer> {
		private final int notificationType;
		private final long accountId;

		ClearNotificationTask(final int notificationType, final long accountId) {
			this.notificationType = notificationType;
			this.accountId = accountId;
		}

		@Override
		protected Integer doInBackground(final Object... params) {
			return clearNotification(mContext, notificationType, accountId);
		}

	}

    final class ClearUnreadCountTask extends AsyncTask<Object, Void, Integer> {
		private final int position;

		ClearUnreadCountTask(final int position) {
			this.position = position;
		}

		@Override
		protected Integer doInBackground(final Object... params) {
			return clearUnreadCount(mContext, position);
		}

	}

    class CreateBlockTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableUser>> {

		private final long account_id, user_id;

		public CreateBlockTask(final long account_id, final long user_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_id = user_id;
		}

		@Override
        protected SingleResponse<ParcelableUser> doInBackground(final Void... params) {
            final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter == null) return SingleResponse.getInstance();
            try {
                final User user = twitter.createBlock(user_id);
                Utils.setLastSeen(mContext, user.getId(), -1);
                for (final Uri uri : STATUSES_URIS) {
                    final Expression where = Expression.and(Expression.equals(Statuses.ACCOUNT_ID, account_id),
                            Expression.equals(Statuses.USER_ID, user_id));
                    mResolver.delete(uri, where.getSQL(), null);

                }
                // I bet you don't want to see this user in your auto complete list.
                //TODO insert to blocked users data
//                final Expression where = Expression.equals(CachedUsers.USER_ID, user_id);
//                mResolver.delete(CachedUsers.CONTENT_URI, where.getSQL(), null);
                return SingleResponse.getInstance(new ParcelableUser(user, account_id), null);
            } catch (final TwitterException e) {
				return SingleResponse.getInstance(null, e);
            }
        }

		@Override
        protected void onPostExecute(final SingleResponse<ParcelableUser> result) {
            if (result.hasData()) {
				final String message = mContext.getString(R.string.blocked_user,
						getUserName(result.getData()));
				mMessagesManager.showInfoMessage(message, false);
                final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
                bus.post(new FriendshipUpdatedEvent(result.getData()));
			} else {
				mMessagesManager.showErrorMessage(R.string.action_blocking, result.getException(), true);
			}
            super.onPostExecute(result);
        }

    }

	class CreateFavoriteTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableStatus>> {

		private final long account_id, status_id;

		public CreateFavoriteTask(final long account_id, final long status_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected SingleResponse<ParcelableStatus> doInBackground(final Void... params) {
			if (account_id < 0) return SingleResponse.getInstance();
            final Twitter twitter = getTwitterInstance(mContext, account_id, true);
			if (twitter == null) return SingleResponse.getInstance();
            try {
                final twitter4j.Status status = twitter.createFavorite(status_id);
                Utils.setLastSeen(mContext, status.getUserMentionEntities(), System.currentTimeMillis());
                final ContentValues values = new ContentValues();
                values.put(Statuses.IS_FAVORITE, true);
                final Expression where = Expression.and(Expression.equals(Statuses.ACCOUNT_ID, account_id),
                        Expression.or(Expression.equals(Statuses.STATUS_ID, status_id),
                                Expression.equals(Statuses.RETWEET_ID, status_id)));
                for (final Uri uri : TwidereDataStore.STATUSES_URIS) {
                    mResolver.update(uri, values, where.getSQL(), null);
                }
				return SingleResponse.getInstance(new ParcelableStatus(status, account_id, false));
            } catch (final TwitterException e) {
				return SingleResponse.getInstance(e);
            }
        }

		@Override
        protected void onPreExecute() {
            super.onPreExecute();
            mCreatingFavoriteIds.put(account_id, status_id);
            final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
            bus.post(new StatusListChangedEvent());
        }

        @Override
		protected void onPostExecute(final SingleResponse<ParcelableStatus> result) {
            mCreatingFavoriteIds.remove(account_id, status_id);
			if (result.hasData()) {
                final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
                bus.post(new FavoriteCreatedEvent(result.getData()));
				mMessagesManager.showOkMessage(R.string.status_favorited, false);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_favoriting, result.getException(), true);
			}
			super.onPostExecute(result);
		}

	}

    class CreateFriendshipTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableUser>> {

        private final long mAccountId;
		private final long user_id;

        public CreateFriendshipTask(final long accountId, final long user_id) {
			super(mContext, mAsyncTaskManager);
            this.mAccountId = accountId;
			this.user_id = user_id;
		}

		public long getAccountId() {
            return mAccountId;
		}

		public long getUserId() {
			return user_id;
		}

        @Override
        protected SingleResponse<ParcelableUser> doInBackground(final Void... params) {

            final Twitter twitter = getTwitterInstance(mContext, mAccountId, false);
			if (twitter == null) return SingleResponse.getInstance();
            try {
                final User user = twitter.createFriendship(user_id);
                Utils.setLastSeen(mContext, user.getId(), System.currentTimeMillis());
                return SingleResponse.getInstance(new ParcelableUser(user, mAccountId), null);
            } catch (final TwitterException e) {
				return SingleResponse.getInstance(null, e);
            }
        }

		@Override
        protected void onPostExecute(final SingleResponse<ParcelableUser> result) {
			if (result.hasData()) {
                final ParcelableUser user = result.getData();
				final String message;
                if (user.is_protected) {
					message = mContext.getString(R.string.sent_follow_request_to_user, getUserName(user));
				} else {
					message = mContext.getString(R.string.followed_user, getUserName(user));
				}
				mMessagesManager.showOkMessage(message, false);
                final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
                bus.post(new FriendshipUpdatedEvent(result.getData()));
			} else {
				mMessagesManager.showErrorMessage(R.string.action_following, result.getException(), false);
			}
			super.onPostExecute(result);
		}

	}

	class CreateMultiBlockTask extends ManagedAsyncTask<Void, Void, ListResponse<Long>> {

		private final long account_id;
		private final long[] user_ids;

		public CreateMultiBlockTask(final long account_id, final long[] user_ids) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_ids = user_ids;
		}

        private void deleteCaches(final List<Long> list) {
            for (final Uri uri : STATUSES_URIS) {
                bulkDelete(mResolver, uri, Statuses.USER_ID, list, Statuses.ACCOUNT_ID + " = " + account_id, false);
            }
            // I bet you don't want to see these users in your auto complete list.
            //TODO insert to blocked users data
//            bulkDelete(mResolver, CachedUsers.CONTENT_URI, CachedUsers.USER_ID, list, null, false);
        }

		@Override
		protected ListResponse<Long> doInBackground(final Void... params) {
            final List<Long> blocked_users = new ArrayList<>();
			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				for (final long user_id : user_ids) {
					try {
						final User user = twitter.createBlock(user_id);
						if (user == null || user.getId() <= 0) {
							continue;
						}
						blocked_users.add(user.getId());
					} catch (final TwitterException e) {
						deleteCaches(blocked_users);
                        return new ListResponse<>(null, e, null);
					}
				}
			}
			deleteCaches(blocked_users);
            return new ListResponse<>(blocked_users, null, null);
		}

		@Override
		protected void onPostExecute(final ListResponse<Long> result) {
			if (result.list != null) {
				mMessagesManager.showInfoMessage(R.string.users_blocked, false);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_blocking, result.getException(), true);
			}
			final Intent intent = new Intent(BROADCAST_MULTI_BLOCKSTATE_CHANGED);
			intent.putExtra(EXTRA_USER_ID, user_ids);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}


    }

    class CreateMuteTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableUser>> {

        private final long mAccountId, mUserId;

        public CreateMuteTask(final long accountId, final long userId) {
            super(mContext, mAsyncTaskManager);
            this.mAccountId = accountId;
            this.mUserId = userId;
		}

        @Override
        protected SingleResponse<ParcelableUser> doInBackground(final Void... params) {
            final Twitter twitter = getTwitterInstance(mContext, mAccountId, false);
            if (twitter == null) return SingleResponse.getInstance();
            try {
                final User user = twitter.createMute(mUserId);
                Utils.setLastSeen(mContext, user.getId(), -1);
                final Expression where = Expression.and(Expression.equals(Statuses.ACCOUNT_ID, mAccountId),
                        Expression.equals(Statuses.USER_ID, mUserId));
                mResolver.delete(Statuses.CONTENT_URI, where.getSQL(), null);

                return SingleResponse.getInstance(new ParcelableUser(user, mAccountId), null);
            } catch (final TwitterException e) {
                return SingleResponse.getInstance(null, e);
	        }
        }

        @Override
        protected void onPostExecute(final SingleResponse<ParcelableUser> result) {
            if (result.hasData()) {
                final String message = mContext.getString(R.string.muted_user,
                        getUserName(result.getData()));
                mMessagesManager.showInfoMessage(message, false);
                final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
                bus.post(new FriendshipUpdatedEvent(result.getData()));
            } else {
                mMessagesManager.showErrorMessage(R.string.action_muting, result.getException(), true);
            }
            super.onPostExecute(result);
        }

    }

	class CreateSavedSearchTask extends ManagedAsyncTask<Void, Void, SingleResponse<SavedSearch>> {

		private final long mAccountId;
		private final String mQuery;

		CreateSavedSearchTask(final long accountId, final String query) {
			super(mContext, mAsyncTaskManager);
			mAccountId = accountId;
			mQuery = query;
		}

		@Override
		protected SingleResponse<SavedSearch> doInBackground(final Void... params) {
			final Twitter twitter = getTwitterInstance(mContext, mAccountId, false);
			if (twitter == null) return null;
			try {
				return SingleResponse.getInstance(twitter.createSavedSearch(mQuery));
			} catch (final TwitterException e) {
				return SingleResponse.getInstance(e);
			}
		}

		@Override
		protected void onPostExecute(final SingleResponse<SavedSearch> result) {
			if (result.hasData()) {
				final String message = mContext.getString(R.string.search_name_saved, result.getData().getQuery());
				mMessagesManager.showOkMessage(message, false);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_saving_search, result.getException(), false);
			}
			super.onPostExecute(result);
		}

	}

	class CreateUserListSubscriptionTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableUserList>> {

		private final long accountId;
		private final long listId;

		public CreateUserListSubscriptionTask(final long accountId, final long listId) {
			super(mContext, mAsyncTaskManager);
			this.accountId = accountId;
			this.listId = listId;
		}

		@Override
        protected SingleResponse<ParcelableUserList> doInBackground(final Void... params) {
            final Twitter twitter = getTwitterInstance(mContext, accountId, false);
			if (twitter == null) return SingleResponse.getInstance();

            try {
                final ParcelableUserList list = new ParcelableUserList(twitter.createUserListSubscription(listId),
						accountId);
                return SingleResponse.getInstance(list);
            } catch (final TwitterException e) {
				return SingleResponse.getInstance(e);
            }
        }

		@Override
		protected void onPostExecute(final SingleResponse<ParcelableUserList> result) {
			final boolean succeed = result.hasData();
			if (succeed) {
				final String message = mContext.getString(R.string.subscribed_to_list, result.getData().name);
				mMessagesManager.showOkMessage(message, false);
                final Intent intent = new Intent(BROADCAST_USER_LIST_SUBSCRIBED);
                intent.putExtra(EXTRA_USER_LIST, result.getData());
                mContext.sendBroadcast(intent);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_subscribing_to_list, result.getException(), true);
			}
			super.onPostExecute(result);
		}

	}

	class CreateUserListTask extends ManagedAsyncTask<Void, Void, SingleResponse<UserList>> {

		private final long account_id;
		private final String list_name, description;
		private final boolean is_public;

		public CreateUserListTask(final long account_id, final String list_name, final boolean is_public,
				final String description) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.list_name = list_name;
			this.description = description;
			this.is_public = is_public;
		}

        @Override
        protected SingleResponse<UserList> doInBackground(final Void... params) {
            final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter == null || list_name == null) return SingleResponse.getInstance();
            try {
                final UserList list = twitter.createUserList(list_name, is_public, description);
				return SingleResponse.getInstance(list, null);
            } catch (final TwitterException e) {
				return SingleResponse.getInstance(null, e);
            }
        }

		@Override
		protected void onPostExecute(final SingleResponse<UserList> result) {
            if (result.hasData()) {
                final UserList userList = result.getData();
                final String message = mContext.getString(R.string.created_list, userList.getName());
				mMessagesManager.showOkMessage(message, false);
                final Intent intent = new Intent(BROADCAST_USER_LIST_CREATED);
                intent.putExtra(EXTRA_USER_LIST, new ParcelableUserList(userList, account_id));
                mContext.sendBroadcast(intent);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_creating_list, result.getException(), true);
			}
			super.onPostExecute(result);
		}

	}

	class DeleteUserListMembersTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableUserList>> {

		private final long mAccountId;
		private final long mUserListId;
		private final ParcelableUser[] users;

		public DeleteUserListMembersTask(final long accountId, final long userListId, final ParcelableUser[] users) {
			super(mContext, mAsyncTaskManager);
			mAccountId = accountId;
			mUserListId = userListId;
			this.users = users;
		}

        @Override
        protected SingleResponse<ParcelableUserList> doInBackground(final Void... params) {
            final Twitter twitter = getTwitterInstance(mContext, mAccountId, false);
			if (twitter == null) return SingleResponse.getInstance();
            try {
                final long[] userIds = new long[users.length];
                for (int i = 0, j = users.length; i < j; i++) {
                    userIds[i] = users[i].id;
                }
                final ParcelableUserList list = new ParcelableUserList(twitter.deleteUserListMembers(mUserListId,
						userIds), mAccountId);
				return SingleResponse.getInstance(list, null);
            } catch (final TwitterException e) {
				return SingleResponse.getInstance(null, e);
            }
        }

		@Override
		protected void onPostExecute(final SingleResponse<ParcelableUserList> result) {
			final boolean succeed = result.hasData() && result.getData().id > 0;
			final String message;
			if (succeed) {
				if (users.length == 1) {
					final ParcelableUser user = users[0];
                    final String displayName = UserColorNameUtils.getDisplayName(mContext, user.name, user.screen_name);
					message = mContext.getString(R.string.deleted_user_from_list, displayName, result.getData().name);
				} else {
					final Resources res = mContext.getResources();
					message = res.getQuantityString(R.plurals.deleted_N_users_from_list, users.length, users.length,
							result.getData().name);
				}
				mMessagesManager.showInfoMessage(message, false);
                final Intent intent = new Intent(BROADCAST_USER_LIST_MEMBERS_DELETED);
                intent.putExtra(EXTRA_USER_LIST, result.getData());
                intent.putExtra(EXTRA_USERS, users);
                mContext.sendBroadcast(intent);
            } else {
                mMessagesManager.showErrorMessage(R.string.action_deleting, result.getException(), true);
            }
			super.onPostExecute(result);
		}

	}

	class DenyFriendshipTask extends ManagedAsyncTask<Void, Void, SingleResponse<User>> {

		private final long mAccountId;
		private final long mUserId;

		public DenyFriendshipTask(final long account_id, final long user_id) {
			super(mContext, mAsyncTaskManager);
			mAccountId = account_id;
			mUserId = user_id;
		}

		public long getAccountId() {
			return mAccountId;
		}

		public long getUserId() {
			return mUserId;
		}

		@Override
		protected SingleResponse<User> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, mAccountId, false);
			if (twitter == null) return SingleResponse.getInstance();
			try {
				final User user = twitter.denyFriendship(mUserId);
				return SingleResponse.getInstance(user, null);
			} catch (final TwitterException e) {
				return SingleResponse.getInstance(null, e);
			}
		}

		@Override
		protected void onPostExecute(final SingleResponse<User> result) {
			if (result.hasData()) {
				final User user = result.getData();
				final String message = mContext.getString(R.string.denied_users_follow_request,
						getUserName(user));
				mMessagesManager.showOkMessage(message, false);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_denying_follow_request, result.getException(), false);
			}
			final Intent intent = new Intent(BROADCAST_FRIENDSHIP_DENIED);
			intent.putExtra(EXTRA_USER_ID, mUserId);
			mContext.sendBroadcast(intent);
			super.onPostExecute(result);
		}

	}

    class DestroyBlockTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableUser>> {

		private final long mAccountId;
		private final long mUserId;

		public DestroyBlockTask(final long accountId, final long userId) {
			super(mContext, mAsyncTaskManager);
			mAccountId = accountId;
			mUserId = userId;
		}

        @Override
        protected SingleResponse<ParcelableUser> doInBackground(final Void... params) {
			final Twitter twitter = getTwitterInstance(mContext, mAccountId, false);
			if (twitter == null) return SingleResponse.getInstance();
            try {
				final User user = twitter.destroyBlock(mUserId);
                Utils.setLastSeen(mContext, user.getId(), -1);
                return SingleResponse.getInstance(new ParcelableUser(user, mAccountId), null);
            } catch (final TwitterException e) {
				return SingleResponse.getInstance(null, e);
            }

        }

		@Override
        protected void onPostExecute(final SingleResponse<ParcelableUser> result) {
			if (result.hasData()) {
				final String message = mContext.getString(R.string.unblocked_user,
						getUserName(result.getData()));
				mMessagesManager.showInfoMessage(message, false);
                final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
                bus.post(new FriendshipUpdatedEvent(result.getData()));
			} else {
				mMessagesManager.showErrorMessage(R.string.action_unblocking, result.getException(), true);
			}
            super.onPostExecute(result);
        }

    }

	class DestroyDirectMessageTask extends ManagedAsyncTask<Void, Void, SingleResponse<DirectMessage>> {

		private final long message_id;
		private final long account_id;

		public DestroyDirectMessageTask(final long account_id, final long message_id) {
			super(mContext, mAsyncTaskManager);

			this.account_id = account_id;
			this.message_id = message_id;
		}

        private void deleteMessages(final long message_id) {
            final String where = DirectMessages.MESSAGE_ID + " = " + message_id;
            mResolver.delete(DirectMessages.Inbox.CONTENT_URI, where, null);
            mResolver.delete(DirectMessages.Outbox.CONTENT_URI, where, null);
        }

		@Override
		protected SingleResponse<DirectMessage> doInBackground(final Void... args) {
			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter == null) return SingleResponse.getInstance();
			try {
				final DirectMessage message = twitter.destroyDirectMessage(message_id);
				deleteMessages(message_id);
				return SingleResponse.getInstance(message, null);
			} catch (final TwitterException e) {
				if (isMessageNotFound(e)) {
					deleteMessages(message_id);
				}
				return SingleResponse.getInstance(null, e);
			}
		}

        private boolean isMessageNotFound(final Exception e) {
            if (!(e instanceof TwitterException)) return false;
            final TwitterException te = (TwitterException) e;
            return te.getErrorCode() == StatusCodeMessageUtils.PAGE_NOT_FOUND
                    || te.getStatusCode() == HttpResponseCode.NOT_FOUND;
        }

		@Override
		protected void onPostExecute(final SingleResponse<DirectMessage> result) {
			super.onPostExecute(result);
			if (result == null) return;
			if (result.hasData() || isMessageNotFound(result.getException())) {
				mMessagesManager.showInfoMessage(R.string.direct_message_deleted, false);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_deleting, result.getException(), true);
			}
		}


    }

	class DestroyFavoriteTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableStatus>> {

		private final long account_id;

		private final long status_id;

		public DestroyFavoriteTask(final long account_id, final long status_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
		protected SingleResponse<ParcelableStatus> doInBackground(final Void... params) {
			if (account_id < 0) return SingleResponse.getInstance();
            final Twitter twitter = getTwitterInstance(mContext, account_id, true);
			if (twitter != null) {
				try {
					final twitter4j.Status status = twitter.destroyFavorite(status_id);
					final ContentValues values = new ContentValues();
					values.put(Statuses.IS_FAVORITE, 0);
                    final Expression where = Expression.and(Expression.equals(Statuses.ACCOUNT_ID, account_id),
                            Expression.or(Expression.equals(Statuses.STATUS_ID, status_id), Expression.equals(Statuses.RETWEET_ID, status_id)));
					for (final Uri uri : TwidereDataStore.STATUSES_URIS) {
                        mResolver.update(uri, values, where.getSQL(), null);
					}
                    return SingleResponse.getInstance(new ParcelableStatus(status, account_id, false));
				} catch (final TwitterException e) {
					return SingleResponse.getInstance(e);
				}
			}
			return SingleResponse.getInstance();
		}

		@Override
        protected void onPreExecute() {
            super.onPreExecute();
            mDestroyingFavoriteIds.put(account_id, status_id);
            final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
            bus.post(new StatusListChangedEvent());
        }

        @Override
		protected void onPostExecute(final SingleResponse<ParcelableStatus> result) {
            mDestroyingFavoriteIds.remove(account_id, status_id);
			if (result.hasData()) {
                final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
                bus.post(new FavoriteDestroyedEvent(result.getData()));
                mMessagesManager.showInfoMessage(R.string.status_unfavorited, false);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_unfavoriting, result.getException(), true);
			}
			super.onPostExecute(result);
		}

	}

    class DestroyFriendshipTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableUser>> {

        private final long mAccountId;
		private final long user_id;

        public DestroyFriendshipTask(final long accountId, final long user_id) {
			super(mContext, mAsyncTaskManager);
            mAccountId = accountId;
			this.user_id = user_id;
		}

		public long getAccountId() {
            return mAccountId;
		}

		public long getUserId() {
			return user_id;
		}

		@Override
        protected SingleResponse<ParcelableUser> doInBackground(final Void... params) {

            final Twitter twitter = getTwitterInstance(mContext, mAccountId, false);
			if (twitter != null) {
				try {
					final User user = twitter.destroyFriendship(user_id);
					// remove user tweets and retweets
                    Utils.setLastSeen(mContext, user.getId(), -1);
                    final Expression where = Expression.and(Expression.equals(Statuses.ACCOUNT_ID, mAccountId),
                            Expression.or(Expression.equals(Statuses.USER_ID, user_id),
                                    Expression.equals(Statuses.RETWEETED_BY_USER_ID, user_id)));
                    mResolver.delete(Statuses.CONTENT_URI, where.getSQL(), null);
                    return SingleResponse.getInstance(new ParcelableUser(user, mAccountId), null);
				} catch (final TwitterException e) {
					return SingleResponse.getInstance(null, e);
				}
			}
			return SingleResponse.getInstance();
		}

		@Override
        protected void onPostExecute(final SingleResponse<ParcelableUser> result) {
			if (result.hasData()) {
				final String message = mContext.getString(R.string.unfollowed_user,
						getUserName(result.getData()));
				mMessagesManager.showInfoMessage(message, false);
                final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
                bus.post(new FriendshipUpdatedEvent(result.getData()));
			} else {
				mMessagesManager.showErrorMessage(R.string.action_unfollowing, result.getException(), true);
			}
			super.onPostExecute(result);
		}

	}

    class DestroyMuteTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableUser>> {

        private final long mAccountId;
        private final long mUserId;

        public DestroyMuteTask(final long accountId, final long userId) {
            super(mContext, mAsyncTaskManager);
            mAccountId = accountId;
            mUserId = userId;
        }

        @Override
        protected SingleResponse<ParcelableUser> doInBackground(final Void... params) {
            final Twitter twitter = getTwitterInstance(mContext, mAccountId, false);
            if (twitter == null) return SingleResponse.getInstance();
            try {
                final User user = twitter.destroyMute(mUserId);
                Utils.setLastSeen(mContext, user.getId(), -1);
                return SingleResponse.getInstance(new ParcelableUser(user, mAccountId), null);
            } catch (final TwitterException e) {
                return SingleResponse.getInstance(null, e);
            }

        }

        @Override
        protected void onPostExecute(final SingleResponse<ParcelableUser> result) {
            if (result.hasData()) {
                final String message = mContext.getString(R.string.unmuted_user,
                        getUserName(result.getData()));
                mMessagesManager.showInfoMessage(message, false);
                final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
                bus.post(new FriendshipUpdatedEvent(result.getData()));
            } else {
                mMessagesManager.showErrorMessage(R.string.action_unmuting, result.getException(), true);
            }
            super.onPostExecute(result);
        }

    }

	class DestroySavedSearchTask extends ManagedAsyncTask<Void, Void, SingleResponse<SavedSearch>> {

		private final long mAccountId;
		private final int mSearchId;

		DestroySavedSearchTask(final long accountId, final int searchId) {
			super(mContext, mAsyncTaskManager);
			mAccountId = accountId;
			mSearchId = searchId;
		}

		@Override
		protected SingleResponse<SavedSearch> doInBackground(final Void... params) {
			final Twitter twitter = getTwitterInstance(mContext, mAccountId, false);
			if (twitter == null) return SingleResponse.getInstance();
			try {
				return SingleResponse.getInstance(twitter.destroySavedSearch(mSearchId));
			} catch (final TwitterException e) {
				return SingleResponse.getInstance(e);
			}
		}

		@Override
		protected void onPostExecute(final SingleResponse<SavedSearch> result) {
			if (result.hasData()) {
				final String message = mContext.getString(R.string.search_name_deleted, result.getData().getQuery());
				mMessagesManager.showOkMessage(message, false);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_deleting_search, result.getException(), false);
			}
			super.onPostExecute(result);
		}

	}

    class DestroyStatusTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableStatus>> {

		private final long account_id;

		private final long status_id;

		public DestroyStatusTask(final long account_id, final long status_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
        protected SingleResponse<ParcelableStatus> doInBackground(final Void... params) {
            final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter == null) return SingleResponse.getInstance();
            ParcelableStatus status = null;
            TwitterException exception = null;
			try {
                status = new ParcelableStatus(twitter.destroyStatus(status_id), account_id, false);
            } catch (final TwitterException e) {
                exception = e;
            }
            if (status != null || (exception != null && exception.getErrorCode() == HttpResponseCode.NOT_FOUND)) {
                final ContentValues values = new ContentValues();
                values.put(Statuses.MY_RETWEET_ID, -1);
                for (final Uri uri : TwidereDataStore.STATUSES_URIS) {
                    mResolver.delete(uri, Statuses.STATUS_ID + " = " + status_id, null);
                    mResolver.update(uri, values, Statuses.MY_RETWEET_ID + " = " + status_id, null);
                }
            }
            return SingleResponse.getInstance(status, exception);
        }

		@Override
        protected void onPreExecute() {
            super.onPreExecute();
            mDestroyingStatusIds.put(account_id, status_id);
            final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
            bus.post(new StatusListChangedEvent());
        }

        @Override
        protected void onPostExecute(final SingleResponse<ParcelableStatus> result) {
            mDestroyingStatusIds.remove(account_id, status_id);
            if (result.hasData()) {
                final ParcelableStatus status = result.getData();
                if (status.retweet_id > 0) {
					mMessagesManager.showInfoMessage(R.string.retweet_cancelled, false);
				} else {
					mMessagesManager.showInfoMessage(R.string.status_deleted, false);
				}
                final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
                bus.post(new StatusDestroyedEvent(status));
			} else {
				mMessagesManager.showErrorMessage(R.string.action_deleting, result.getException(), true);
			}
			super.onPostExecute(result);
		}

	}

	class DestroyUserListSubscriptionTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableUserList>> {

		private final long mAccountId;
		private final long mListId;

		public DestroyUserListSubscriptionTask(final long accountId, final long listId) {
			super(mContext, mAsyncTaskManager);
			mAccountId = accountId;
			mListId = listId;
		}

		@Override
		protected SingleResponse<ParcelableUserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, mAccountId, false);
			if (twitter != null) {
				try {
					final ParcelableUserList list = new ParcelableUserList(
							twitter.destroyUserListSubscription(mListId), mAccountId);
					return SingleResponse.getInstance(list, null);
				} catch (final TwitterException e) {
					return SingleResponse.getInstance(null, e);
				}
			}
			return SingleResponse.getInstance();
		}

		@Override
		protected void onPostExecute(final SingleResponse<ParcelableUserList> result) {
			final boolean succeed = result.hasData();
			if (succeed) {
				final String message = mContext.getString(R.string.unsubscribed_from_list, result.getData().name);
				mMessagesManager.showOkMessage(message, false);
                final Intent intent = new Intent(BROADCAST_USER_LIST_UNSUBSCRIBED);
                intent.putExtra(EXTRA_USER_LIST, result.getData());
                mContext.sendBroadcast(intent);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_unsubscribing_from_list, result.getException(), true);
			}
			super.onPostExecute(result);
		}

	}

	class DestroyUserListTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableUserList>> {

		private final long mAccountId;
		private final long mListId;

		public DestroyUserListTask(final long accountId, final long listId) {
			super(mContext, mAsyncTaskManager);
			mAccountId = accountId;
			mListId = listId;
		}

		@Override
		protected SingleResponse<ParcelableUserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, mAccountId, false);
			if (twitter != null) {
				try {
					if (mListId > 0) {
						final ParcelableUserList list = new ParcelableUserList(twitter.destroyUserList(mListId),
								mAccountId);
                        return SingleResponse.getInstance(list);
					}
				} catch (final TwitterException e) {
					return SingleResponse.getInstance(e);
				}
			}
			return SingleResponse.getInstance();
		}

		@Override
		protected void onPostExecute(final SingleResponse<ParcelableUserList> result) {
			final boolean succeed = result.hasData();
			if (succeed) {
				final String message = mContext.getString(R.string.deleted_list, result.getData().name);
				mMessagesManager.showInfoMessage(message, false);
                final Intent intent = new Intent(BROADCAST_USER_LIST_DELETED);
                intent.putExtra(EXTRA_USER_LIST, result.getData());
                mContext.sendBroadcast(intent);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_deleting, result.getException(), true);
			}
			super.onPostExecute(result);
		}

	}

	abstract class GetDirectMessagesTask extends ManagedAsyncTask<Void, Void, List<MessageListResponse>> {

		private final long[] account_ids, max_ids, since_ids;

		public GetDirectMessagesTask(final long[] account_ids, final long[] max_ids, final long[] since_ids,
				final String tag) {
			super(mContext, mAsyncTaskManager, tag);
			this.account_ids = account_ids;
			this.max_ids = max_ids;
			this.since_ids = since_ids;
		}

		public abstract ResponseList<DirectMessage> getDirectMessages(Twitter twitter, Paging paging)
				throws TwitterException;

        final boolean isMaxIdsValid() {
            return max_ids != null && max_ids.length == account_ids.length;
        }

		@Override
		protected List<MessageListResponse> doInBackground(final Void... params) {

            final List<MessageListResponse> result = new ArrayList<>();

			if (account_ids == null) return result;

			int idx = 0;
			final int load_item_limit = mPreferences.getInt(KEY_LOAD_ITEM_LIMIT, DEFAULT_LOAD_ITEM_LIMIT);
            for (final long accountId : account_ids) {
                final Twitter twitter = getTwitterInstance(mContext, accountId, true);
                if (twitter == null) continue;
                try {
                    final Paging paging = new Paging();
                    paging.setCount(load_item_limit);
                    long max_id = -1, since_id = -1;
                    if (isMaxIdsValid() && max_ids[idx] > 0) {
                        max_id = max_ids[idx];
                        paging.setMaxId(max_id);
                    }
                    if (isSinceIdsValid() && since_ids[idx] > 0) {
                        since_id = since_ids[idx];
                        paging.setSinceId(since_id - 1);
                    }
                    final List<DirectMessage> messages = new ArrayList<>();
                    final boolean truncated = truncateMessages(getDirectMessages(twitter, paging), messages,
                            since_id);
                    result.add(new MessageListResponse(accountId, max_id, since_id, load_item_limit, messages,
                            truncated));
                    storeMessages(accountId, messages, isOutgoing(), true);
                } catch (final TwitterException e) {
                    if (Utils.isDebugBuild()) {
                        Log.w(LOGTAG, e);
                    }
                    result.add(new MessageListResponse(accountId, e));
                }
				idx++;
			}
			return result;

		}

        protected abstract boolean isOutgoing();

        private boolean storeMessages(long accountId, List<DirectMessage> messages, boolean isOutgoing, boolean notify) {
            if (messages == null) return true;
            final Uri uri = getDatabaseUri();
            final ContentValues[] valuesArray = new ContentValues[messages.size()];
            final long[] messageIds = new long[messages.size()];

            for (int i = 0, j = messages.size(); i < j; i++) {
                final DirectMessage message = messages.get(i);
                messageIds[i] = message.getId();
                valuesArray[i] = createDirectMessage(message, accountId, isOutgoing);
            }

            // Delete all rows conflicting before new data inserted.
//            final Expression deleteWhere = Expression.and(Expression.equals(DirectMessages.ACCOUNT_ID, accountId),
//                    Expression.in(new Column(DirectMessages.MESSAGE_ID), new RawItemArray(messageIds)));
//            final Uri deleteUri = UriUtils.appendQueryParameters(uri, QUERY_PARAM_NOTIFY, false);
//            mResolver.delete(deleteUri, deleteWhere.getSQL(), null);



            // Insert previously fetched items.
            final Uri insertUri = UriUtils.appendQueryParameters(uri, QUERY_PARAM_NOTIFY, notify);
            bulkInsert(mResolver, insertUri, valuesArray);
            return false;
        }

        protected abstract Uri getDatabaseUri();

        final boolean isSinceIdsValid() {
            return since_ids != null && since_ids.length == account_ids.length;
        }

		@Override
        protected void onPreExecute() {
            super.onPreExecute();
            final Bus bus = TwittnukerApplication.getInstance(getContext()).getMessageBus();
            bus.post(new GetMessagesTaskEvent(getDatabaseUri(), true));
        }

        @Override
		protected void onPostExecute(final List<MessageListResponse> result) {
			super.onPostExecute(result);
            final Bus bus = TwittnukerApplication.getInstance(getContext()).getMessageBus();
            bus.post(new GetMessagesTaskEvent(getDatabaseUri(), false));
			for (final TwitterListResponse<DirectMessage> response : result) {
				if (response.list == null) {
					mMessagesManager.showErrorMessage(R.string.action_refreshing_direct_messages,
							response.getException(), true);
				}
			}
		}


    }

	class GetHomeTimelineTask extends GetStatusesTask {

		public GetHomeTimelineTask(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			super(account_ids, max_ids, since_ids, TASK_TAG_GET_HOME_TIMELINE);
		}

		@Override
        protected Uri getDatabaseUri() {
            return Statuses.CONTENT_URI;
        }

        @Override
		public ResponseList<twitter4j.Status> getStatuses(final Twitter twitter, final Paging paging)
				throws TwitterException {
			return twitter.getHomeTimeline(paging);
		}

		@Override
		protected void onPostExecute(final List<StatusListResponse> responses) {
			super.onPostExecute(responses);
			mGetHomeTimelineTaskId = -1;
			for (final StatusListResponse response : responses) {
				if (response.list == null) {
					mMessagesManager.showErrorMessage(R.string.action_refreshing_home_timeline,
							response.getException(), true);
					break;
				}
			}
		}

		@Override
		protected void onPreExecute() {
			final Intent intent = new Intent(BROADCAST_RESCHEDULE_HOME_TIMELINE_REFRESHING);
			mContext.sendBroadcast(intent);
			super.onPreExecute();
		}

	}

	class GetLocalTrendsTask extends GetTrendsTask {

		private final int woeid;

		public GetLocalTrendsTask(final long account_id, final int woeid) {
			super(account_id);
			this.woeid = woeid;
		}

		@Override
		public List<Trends> getTrends(final Twitter twitter) throws TwitterException {
            final ArrayList<Trends> trends_list = new ArrayList<>();
			if (twitter != null) {
				trends_list.add(twitter.getLocationTrends(woeid));
			}
			return trends_list;
		}

		@Override
		protected void onPostExecute(final ListResponse<Trends> result) {
			mAsyncTaskManager.add(new StoreLocalTrendsTask(result), true);
			super.onPostExecute(result);

		}

	}

	class GetMentionsTask extends GetStatusesTask {

		public GetMentionsTask(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			super(account_ids, max_ids, since_ids, TASK_TAG_GET_MENTIONS);
		}


		@Override
		public ResponseList<twitter4j.Status> getStatuses(final Twitter twitter, final Paging paging)
				throws TwitterException {
			return twitter.getMentionsTimeline(paging);
		}

		@Override
        protected Uri getDatabaseUri() {
            return Mentions.CONTENT_URI;
        }

        @Override
		protected void onPostExecute(final List<StatusListResponse> responses) {
			super.onPostExecute(responses);
//            mAsyncTaskManager.add(new StoreMentionsTask(responses, !isMaxIdsValid()), true);
			mGetMentionsTaskId = -1;
			for (final StatusListResponse response : responses) {
				if (response.list == null) {
					mMessagesManager.showErrorMessage(R.string.action_refreshing_mentions, response.getException(),
							true);
					break;
				}
			}
		}

		@Override
		protected void onPreExecute() {

			final Intent intent = new Intent(BROADCAST_RESCHEDULE_MENTIONS_REFRESHING);
			mContext.sendBroadcast(intent);
			super.onPreExecute();
		}

	}

	class GetReceivedDirectMessagesTask extends GetDirectMessagesTask {

		public GetReceivedDirectMessagesTask(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			super(account_ids, max_ids, since_ids, TASK_TAG_GET_RECEIVED_DIRECT_MESSAGES);
		}

		@Override
        protected Uri getDatabaseUri() {
            return Inbox.CONTENT_URI;
        }

        @Override
		public ResponseList<DirectMessage> getDirectMessages(final Twitter twitter, final Paging paging)
				throws TwitterException {
			return twitter.getDirectMessages(paging);
		}

		@Override
        protected boolean isOutgoing() {
            return false;
        }

        @Override
		protected void onPostExecute(final List<MessageListResponse> responses) {
			super.onPostExecute(responses);
//            mAsyncTaskManager.add(new StoreReceivedDirectMessagesTask(responses, !isMaxIdsValid()), true);
			mGetReceivedDirectMessagesTaskId = -1;
		}

		@Override
		protected void onPreExecute() {
			final Intent intent = new Intent(BROADCAST_RESCHEDULE_DIRECT_MESSAGES_REFRESHING);
			mContext.sendBroadcast(intent);
			super.onPreExecute();
		}

	}

	class GetSentDirectMessagesTask extends GetDirectMessagesTask {

		public GetSentDirectMessagesTask(final long[] account_ids, final long[] max_ids, final long[] since_ids) {
			super(account_ids, max_ids, since_ids, TASK_TAG_GET_SENT_DIRECT_MESSAGES);
		}

		@Override
		public ResponseList<DirectMessage> getDirectMessages(final Twitter twitter, final Paging paging)
				throws TwitterException {
			return twitter.getSentDirectMessages(paging);
		}

		@Override
        protected boolean isOutgoing() {
            return true;
        }

        @Override
        protected Uri getDatabaseUri() {
            return Outbox.CONTENT_URI;
        }

        @Override
		protected void onPostExecute(final List<MessageListResponse> responses) {
			super.onPostExecute(responses);
//            mAsyncTaskManager.add(new StoreSentDirectMessagesTask(responses, !isMaxIdsValid()), true);
			mGetSentDirectMessagesTaskId = -1;
		}

	}

	abstract class GetStatusesTask extends ManagedAsyncTask<Void, Void, List<StatusListResponse>> {

        private final long[] mAccountIds, mMaxIds, mSinceIds;

		public GetStatusesTask(final long[] account_ids, final long[] max_ids, final long[] since_ids, final String tag) {
			super(mContext, mAsyncTaskManager, tag);
            mAccountIds = account_ids;
            mMaxIds = max_ids;
            mSinceIds = since_ids;
		}

		public abstract ResponseList<twitter4j.Status> getStatuses(Twitter twitter, Paging paging)
				throws TwitterException;

        final boolean isMaxIdsValid() {
            return mMaxIds != null && mMaxIds.length == mAccountIds.length;
        }

		@Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
//            new CacheUsersStatusesTask(mContext, responses.toArray(array)).executeTask();
        }

        private void storeStatus(long accountId, List<twitter4j.Status> statuses, long maxId, boolean truncated, boolean notify) {
            if (statuses == null || statuses.isEmpty() || accountId <= 0) {
                return;
            }
            final Uri uri = getDatabaseUri();
            final boolean noItemsBefore = getStatusCountInDatabase(mContext, uri, accountId) <= 0;
            final ContentValues[] values = new ContentValues[statuses.size()];
            final long[] statusIds = new long[statuses.size()];
            long minId = -1;
            int minIdx = -1;
            for (int i = 0, j = statuses.size(); i < j; i++) {
                final twitter4j.Status status = statuses.get(i);
                values[i] = createStatus(status, accountId);
                final long id = status.getId();
                if (minId == -1 || id < minId) {
                    minId = id;
                    minIdx = i;
                }
                statusIds[i] = id;
            }
            // Delete all rows conflicting before new data inserted.
            final Expression accountWhere = Expression.equals(Statuses.ACCOUNT_ID, accountId);
            final Expression statusWhere = Expression.in(new Column(Statuses.STATUS_ID), new RawItemArray(statusIds));
            final String countWhere = Expression.and(accountWhere, statusWhere).getSQL();
            final String[] projection = {SQLFunctions.COUNT()};
            final int rowsDeleted;
            final Cursor countCur = mResolver.query(uri, projection, countWhere, null, null);
            if (countCur.moveToFirst()) {
                rowsDeleted = countCur.getInt(0);
            } else {
                rowsDeleted = 0;
            }
            countCur.close();

            // Insert a gap.
            final boolean deletedOldGap = rowsDeleted > 0 && ArrayUtils.contains(statusIds, maxId);
            final boolean noRowsDeleted = rowsDeleted == 0;
            final boolean insertGap = minId > 0 && (noRowsDeleted || deletedOldGap) && !truncated
                    && !noItemsBefore && statuses.size() > 1;
            if (insertGap && minIdx != -1) {
                values[minIdx].put(Statuses.IS_GAP, true);
            }
            // Insert previously fetched items.
            final Uri insertUri = UriUtils.appendQueryParameters(uri, QUERY_PARAM_NOTIFY, notify);
            bulkInsert(mResolver, insertUri, values);

        }

        @NonNull
        protected abstract Uri getDatabaseUri();

        @Override
        protected void onPostExecute(List<StatusListResponse> statusListResponses) {
            super.onPostExecute(statusListResponses);
            final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
            bus.post(new GetStatusesTaskEvent(getDatabaseUri(), false));
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
            bus.post(new GetStatusesTaskEvent(getDatabaseUri(), true));
        }

        @Override
		protected List<StatusListResponse> doInBackground(final Void... params) {
            final List<StatusListResponse> result = new ArrayList<>();
            if (mAccountIds == null) return result;
			int idx = 0;
            final int loadItemLimit = mPreferences.getInt(KEY_LOAD_ITEM_LIMIT, DEFAULT_LOAD_ITEM_LIMIT);
            for (final long accountId : mAccountIds) {
                final Twitter twitter = getTwitterInstance(mContext, accountId, true);
                if (twitter == null) continue;
                try {
                    final Paging paging = new Paging();
                    paging.setCount(loadItemLimit);
                    final long maxId, sinceId;
                    if (isMaxIdsValid() && mMaxIds[idx] > 0) {
                        maxId = mMaxIds[idx];
                        paging.setMaxId(maxId);
                    } else {
                        maxId = -1;
                    }
                    if (isSinceIdsValid() && mSinceIds[idx] > 0) {
                        sinceId = mSinceIds[idx];
                        paging.setSinceId(sinceId - 1);
                    } else {
                        sinceId = -1;
                    }
                    final List<twitter4j.Status> statuses = new ArrayList<>();
                    final boolean truncated = truncateStatuses(getStatuses(twitter, paging), statuses, sinceId);
                    storeStatus(accountId, statuses, maxId, truncated, true);
                } catch (final TwitterException e) {
                    Log.w(LOGTAG, e);
                    result.add(new StatusListResponse(accountId, e));
                }
				idx++;
			}
			return result;
		}

		final boolean isSinceIdsValid() {
            return mSinceIds != null && mSinceIds.length == mAccountIds.length;
		}


	}

	abstract class GetTrendsTask extends ManagedAsyncTask<Void, Void, ListResponse<Trends>> {

		private final long account_id;

		public GetTrendsTask(final long account_id) {
			super(mContext, mAsyncTaskManager, TASK_TAG_GET_TRENDS);
			this.account_id = account_id;
		}

		public abstract List<Trends> getTrends(Twitter twitter) throws TwitterException;

		@Override
		protected ListResponse<Trends> doInBackground(final Void... params) {
			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			final Bundle extras = new Bundle();
			extras.putLong(EXTRA_ACCOUNT_ID, account_id);
			if (twitter != null) {
				try {
                    return new ListResponse<>(getTrends(twitter), null, extras);
				} catch (final TwitterException e) {
                    return new ListResponse<>(null, e, extras);
				}
			}
            return new ListResponse<>(null, null, extras);
		}

	}

    final class RemoveUnreadCountsTask extends AsyncTask<Object, Void, Integer> {
		private final int position;
        private final LongSparseArray<Set<Long>> counts;

        RemoveUnreadCountsTask(final int position, final LongSparseArray<Set<Long>> counts) {
			this.position = position;
			this.counts = counts;
		}

		@Override
		protected Integer doInBackground(final Object... params) {
			return removeUnreadCounts(mContext, position, counts);
		}

	}

	class ReportMultiSpamTask extends ManagedAsyncTask<Void, Void, ListResponse<Long>> {

		private final long account_id;
		private final long[] user_ids;

		public ReportMultiSpamTask(final long account_id, final long[] user_ids) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.user_ids = user_ids;
		}

		@Override
		protected ListResponse<Long> doInBackground(final Void... params) {

			final Bundle extras = new Bundle();
			extras.putLong(EXTRA_ACCOUNT_ID, account_id);
            final List<Long> reported_users = new ArrayList<>();
			final Twitter twitter = getTwitterInstance(mContext, account_id, false);
			if (twitter != null) {
				for (final long user_id : user_ids) {
					try {
						final User user = twitter.reportSpam(user_id);
						if (user == null || user.getId() <= 0) {
							continue;
						}
						reported_users.add(user.getId());
					} catch (final TwitterException e) {
                        return new ListResponse<>(null, e, extras);
					}
				}
			}
            return new ListResponse<>(reported_users, null, extras);
		}

		@Override
		protected void onPostExecute(final ListResponse<Long> result) {
			if (result != null) {
				final String user_id_where = ListUtils.toString(result.list, ',', false);
				for (final Uri uri : STATUSES_URIS) {
                    final Expression where = Expression.and(Expression.equals(Statuses.ACCOUNT_ID, account_id),
                            new Expression(String.format(Locale.ROOT, "%s IN (%s)", Statuses.USER_ID, user_id_where)));
                    mResolver.delete(uri, where.getSQL(), null);
				}
				mMessagesManager.showInfoMessage(R.string.reported_users_for_spam, false);
                final Intent intent = new Intent(BROADCAST_MULTI_BLOCKSTATE_CHANGED);
                intent.putExtra(EXTRA_USER_IDS, user_ids);
                intent.putExtra(EXTRA_ACCOUNT_ID, account_id);
                mContext.sendBroadcast(intent);
            }
			super.onPostExecute(result);
		}

	}

    class ReportSpamTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableUser>> {

        private final long mAccountId;
		private final long user_id;

        public ReportSpamTask(final long accountId, final long user_id) {
			super(mContext, mAsyncTaskManager);
            this.mAccountId = accountId;
			this.user_id = user_id;
		}

		@Override
        protected SingleResponse<ParcelableUser> doInBackground(final Void... params) {
            final Twitter twitter = getTwitterInstance(mContext, mAccountId, false);
			if (twitter != null) {
				try {
					final User user = twitter.reportSpam(user_id);
                    return SingleResponse.getInstance(new ParcelableUser(user, mAccountId), null);
				} catch (final TwitterException e) {
					return SingleResponse.getInstance(null, e);
				}
			}
			return SingleResponse.getInstance();
		}

		@Override
        protected void onPostExecute(final SingleResponse<ParcelableUser> result) {
            if (result.hasData()) {
				for (final Uri uri : STATUSES_URIS) {
                    final String where = Statuses.ACCOUNT_ID + " = " + mAccountId + " AND " + Statuses.USER_ID + " = "
							+ user_id;
					mResolver.delete(uri, where, null);
				}
				mMessagesManager.showInfoMessage(R.string.reported_user_for_spam, false);
                final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
                bus.post(new FriendshipUpdatedEvent(result.getData()));
			} else {
				mMessagesManager.showErrorMessage(R.string.action_reporting_for_spam, result.getException(), true);
			}
			super.onPostExecute(result);
		}

	}

    class RetweetStatusTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableStatus>> {

		private final long account_id;

		private final long status_id;

		public RetweetStatusTask(final long account_id, final long status_id) {
			super(mContext, mAsyncTaskManager);
			this.account_id = account_id;
			this.status_id = status_id;
		}

		@Override
        protected SingleResponse<ParcelableStatus> doInBackground(final Void... params) {
			if (account_id < 0) return SingleResponse.getInstance();
            final Twitter twitter = getTwitterInstance(mContext, account_id, true);
            if (twitter == null) {
                return SingleResponse.getInstance();
            }
            try {
                final twitter4j.Status status = twitter.retweetStatus(status_id);
                Utils.setLastSeen(mContext, status.getUserMentionEntities(), System.currentTimeMillis());
                return SingleResponse.getInstance(new ParcelableStatus(status, account_id, false));
            } catch (final TwitterException e) {
                return SingleResponse.getInstance(e);
            }
        }

		@Override
        protected void onPreExecute() {
            super.onPreExecute();
            mCreatingRetweetIds.put(account_id, status_id);
            final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
            bus.post(new StatusListChangedEvent());
        }

        @Override
        protected void onPostExecute(final SingleResponse<ParcelableStatus> result) {
            mCreatingRetweetIds.remove(account_id, status_id);
            if (result.hasData()) {
				final ContentValues values = new ContentValues();
                final ParcelableStatus status = result.getData();
                values.put(Statuses.MY_RETWEET_ID, status.id);
                final Expression where = Expression.or(
                        Expression.equals(Statuses.STATUS_ID, status_id),
                        Expression.equals(Statuses.RETWEET_ID, status_id)
                );
				for (final Uri uri : STATUSES_URIS) {
                    mResolver.update(uri, values, where.getSQL(), null);
				}
                final Bus bus = TwittnukerApplication.getInstance(mContext).getMessageBus();
                bus.post(new StatusRetweetedEvent(status));
				mMessagesManager.showOkMessage(R.string.status_retweeted, false);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_retweeting, result.getException(), true);
			}
			super.onPostExecute(result);
		}

	}


	class StoreLocalTrendsTask extends StoreTrendsTask {

		public StoreLocalTrendsTask(final ListResponse<Trends> result) {
			super(result, CachedTrends.Local.CONTENT_URI);
		}

	}

    class StoreTrendsTask extends ManagedAsyncTask<Void, Void, SingleResponse<Boolean>> {

		private final ListResponse<Trends> response;
		private final Uri uri;

		public StoreTrendsTask(final ListResponse<Trends> result, final Uri uri) {
			super(mContext, mAsyncTaskManager, TASK_TAG_STORE_TRENDS);
			response = result;
			this.uri = uri;
		}

		@Override
        protected SingleResponse<Boolean> doInBackground(final Void... args) {
            if (response == null) return SingleResponse.getInstance(false);
			final List<Trends> messages = response.list;
            final ArrayList<String> hashtags = new ArrayList<>();
            final ArrayList<ContentValues> hashtagValues = new ArrayList<>();
            if (messages != null && messages.size() > 0) {
                final ContentValues[] valuesArray = createTrends(messages);
                for (final ContentValues values : valuesArray) {
					final String hashtag = values.getAsString(CachedTrends.NAME).replaceFirst("#", "");
					if (hashtags.contains(hashtag)) {
						continue;
					}
					hashtags.add(hashtag);
                    final ContentValues hashtagValue = new ContentValues();
                    hashtagValue.put(CachedHashtags.NAME, hashtag);
                    hashtagValues.add(hashtagValue);
				}
				mResolver.delete(uri, null, null);
                bulkInsert(mResolver, uri, valuesArray);
				bulkDelete(mResolver, CachedHashtags.CONTENT_URI, CachedHashtags.NAME, hashtags, null, true);
				bulkInsert(mResolver, CachedHashtags.CONTENT_URI,
                        hashtagValues.toArray(new ContentValues[hashtagValues.size()]));
			}
            return SingleResponse.getInstance(true);
		}

		@Override
        protected void onPostExecute(final SingleResponse<Boolean> response) {
			// if (response != null && response.data != null &&
			// response.data.getBoolean(EXTRA_SUCCEED)) {
			// final Intent intent = new Intent(BROADCAST_TRENDS_UPDATED);
			// intent.putExtra(EXTRA_SUCCEED, true);
			// mContext.sendBroadcast(intent);
			// }
			super.onPostExecute(response);
		}

	}

	class UpdateUserListDetailsTask extends ManagedAsyncTask<Void, Void, SingleResponse<ParcelableUserList>> {

		private final long accountId;
		private final long listId;
		private final boolean isPublic;
		private final String name, description;

		public UpdateUserListDetailsTask(final long accountId, final long listId, final boolean isPublic,
				final String name, final String description) {
			super(mContext, mAsyncTaskManager);
			this.accountId = accountId;
			this.listId = listId;
			this.name = name;
			this.isPublic = isPublic;
			this.description = description;
		}

		@Override
		protected SingleResponse<ParcelableUserList> doInBackground(final Void... params) {

			final Twitter twitter = getTwitterInstance(mContext, accountId, false);
			if (twitter != null) {
				try {
					final UserList list = twitter.updateUserList(listId, name, isPublic, description);
                    return SingleResponse.getInstance(new ParcelableUserList(list, accountId));
				} catch (final TwitterException e) {
					return SingleResponse.getInstance(e);
				}
			}
			return SingleResponse.getInstance();
		}

		@Override
		protected void onPostExecute(final SingleResponse<ParcelableUserList> result) {
			if (result.hasData() && result.getData().id > 0) {
				final String message = mContext.getString(R.string.updated_list_details, result.getData().name);
				mMessagesManager.showOkMessage(message, false);
                final Intent intent = new Intent(BROADCAST_USER_LIST_DETAILS_UPDATED);
                intent.putExtra(EXTRA_LIST_ID, listId);
                mContext.sendBroadcast(intent);
			} else {
				mMessagesManager.showErrorMessage(R.string.action_updating_details, result.getException(), true);
			}
			super.onPostExecute(result);
		}

	}

}