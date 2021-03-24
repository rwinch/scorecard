package io.spring.team.scorecard;

import io.spring.team.scorecard.graphql.GraphQLClient;
import org.junit.jupiter.api.Test;

import java.util.List;

class ScorecardApplicationTests {

	@Test
	void contextLoads() {
		GraphQLClient client = new GraphQLClient("");
//		System.out.println(client.findIssueId().block());

		String repositoryId = client.findRepositoryId("rwinch", "github-graphql-explore").block();
		List<Integer> ids = client.findByTitle(repositoryId, "Update org.springframework 5.2.0").collectList().block();
		System.out.println(ids);
//
//		String title = "Update org.springframework 5.2.0";
//
//		String branchRef = "fix-5";
//
//		String pullRequestId = client.createPullRequest(repositoryId, "main", title, title, branchRef).block();
//
//		String enabledMergeAt = client.enablePullRequestAutoMerge(pullRequestId).block();
//
//		System.out.println(enabledMergeAt);
	}

}
