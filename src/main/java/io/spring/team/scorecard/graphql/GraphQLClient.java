package io.spring.team.scorecard.graphql;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Input;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import io.spring.team.scorecard.*;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class GraphQLClient {

	private final Log logger = LogFactory.getLog(GraphQLClient.class);

	private final ApolloClient apolloClient;

	public GraphQLClient(String githubToken) {
		OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
		clientBuilder.addInterceptor(new AuthorizationInterceptor(githubToken));
		this.apolloClient = ApolloClient.builder()
				.serverUrl("https://api.github.com/graphql")
				.okHttpClient(clientBuilder.build())
				.build();
	}

	public Mono<String> findIssueId() {
		return Mono.create( sink -> this.apolloClient.query(new FindIssueIDQuery())
				.enqueue(new ApolloCall.Callback<FindIssueIDQuery.Data>() {
					@Override
					public void onResponse(@NotNull Response<FindIssueIDQuery.Data> response) {
						sink.success(response.getData().repository().issue().id());
					}
					@Override
					public void onFailure(@NotNull ApolloException e) {
						sink.error(e);
					}
				}));
	}

	public Mono<String> findRepositoryId(String owner, String name) {
		return Mono.create( sink -> this.apolloClient.query(new FindRepositoryIdQuery(owner, name))
				.enqueue(new ApolloCall.Callback<FindRepositoryIdQuery.Data>() {
					@Override
					public void onResponse(@NotNull Response<FindRepositoryIdQuery.Data> response) {
						if (response.hasErrors()) {
							sink.error(new RuntimeException(response.getErrors().stream().map(e -> e.getMessage()).collect(Collectors.joining(" "))));
						} else {
							sink.success(response.getData().repository().id());
						}
					}
					@Override
					public void onFailure(@NotNull ApolloException e) {
						sink.error(e);
					}
				}));
	}

	public Mono<String> createPullRequest(String repositoryId, String baseRefName, String title, String body, String headRefName) {
		return Mono.create(sink -> this.apolloClient.mutate(new CreatePullRequestMutation(baseRefName, Input.optional(body), headRefName, repositoryId, title))
				.enqueue(new ApolloCall.Callback<CreatePullRequestMutation.Data>() {
					@Override
					public void onResponse(@NotNull Response<CreatePullRequestMutation.Data> response) {
						if (response.hasErrors()) {
							sink.error(new RuntimeException(response.getErrors().stream().map(e -> e.getMessage()).collect(Collectors.joining(" "))));
						} else {
							sink.success(response.getData().createPullRequest().pullRequest().id());
						}
					}

					@Override
					public void onFailure(@NotNull ApolloException e) {
						sink.error(e);
					}
				}));
	}


	public Flux<Integer> findByTitle(String repositoryId, String title) {
		return Flux.create(sink -> this.apolloClient.query(new FindPullRequestByTitleQuery("'Update org.springframework 5.2.0' in:title"))
				.enqueue(new ApolloCall.Callback<FindPullRequestByTitleQuery.Data>() {
					@Override
					public void onResponse(@NotNull Response<FindPullRequestByTitleQuery.Data> response) {
						if (response.hasErrors()) {
							sink.error(new RuntimeException(response.getErrors().stream().map(e -> e.getMessage()).collect(Collectors.joining(" "))));
						} else {
							List<FindPullRequestByTitleQuery.AsPullRequest> issues = response.getData().search().nodes().stream().map(FindPullRequestByTitleQuery.AsPullRequest.class::cast).collect(Collectors.toList());
							System.out.println(issues);
							sink.next(response.getData().search().issueCount());
							sink.complete();
						}
					}

					@Override
					public void onFailure(@NotNull ApolloException e) {
						sink.error(e);
					}
				}));
	}

	public Mono<String> enablePullRequestAutoMerge(String pullRequestId) {
		return Mono.create(sink -> this.apolloClient.mutate(new EnablePullRequestAutoMergeMutation(pullRequestId))
				.enqueue(new ApolloCall.Callback<EnablePullRequestAutoMergeMutation.Data>() {
					@Override
					public void onResponse(@NotNull Response<EnablePullRequestAutoMergeMutation.Data> response) {
						if (response.hasErrors()) {
							sink.error(new RuntimeException(response.getErrors().stream().map(e -> e.getMessage()).collect(Collectors.joining(" "))));
						} else {
							sink.success(String.valueOf(response.getData().enablePullRequestAutoMerge().pullRequest().autoMergeRequest().enabledAt()));
						}
					}

					@Override
					public void onFailure(@NotNull ApolloException e) {
						sink.error(e);
					}
				}));
	}

	public Mono<Integer> searchNumberOfIssuesAndPRs(String searchQuery) {
		logger.debug("query: " + searchQuery);
		return Mono.create(sink -> {
			this.apolloClient.query(new IssueCountQuery(searchQuery))
					.enqueue(new ApolloCall.Callback<IssueCountQuery.Data>() {
						@Override
						public void onResponse(@NotNull Response<IssueCountQuery.Data> response) {
							sink.success(response.getData().search().issueCount());
						}

						@Override
						public void onFailure(@NotNull ApolloException e) {
							sink.error(e);
						}
					});
		});
	}

	public Flux<String> findAssignableUsers(String org, String repo) {
		return Flux.create(sink -> {
			this.apolloClient.query(new AssignableUsersQuery(org, repo))
					.enqueue(new ApolloCall.Callback<AssignableUsersQuery.Data>() {
						@Override
						public void onResponse(@NotNull Response<AssignableUsersQuery.Data> response) {
							response.getData().repository().assignableUsers()
									.nodes().forEach(node -> sink.next(node.login()));
							sink.complete();
						}

						@Override
						public void onFailure(@NotNull ApolloException e) {
							sink.error(e);
						}
					});
		});
	}

	private static class AuthorizationInterceptor implements Interceptor {

		private final String token;

		public AuthorizationInterceptor(String token) {
			this.token = token;
		}

		@Override
		public okhttp3.Response intercept(Chain chain) throws IOException {
			Request request = chain.request().newBuilder()
					.addHeader("Authorization", "Bearer " + this.token).build();
			return chain.proceed(request);
		}
	}
}
