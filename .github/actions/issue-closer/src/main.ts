import * as core from '@actions/core';
import * as github from '@actions/github';

interface Rule {
    type: 'title' | 'body';
    regex: string;
    message: string;
}

async function run() {
    try {
        // Get client and context
        const client: github.GitHub = new github.GitHub(
            core.getInput('repo-token', {required: true})
        );
        const context = github.context;
        const payload = context.payload;

        // Do nothing if it wasn't being opened or it's not an issue
        if (payload.action !== 'opened' || !payload.issue) {
            return;
        }

        if (!payload.sender) {
            throw new Error('Internal error, no sender provided by GitHub');
        }

        const issue: {owner: string; repo: string; number: number} = context.issue;

        const rules: Rule[] = [
            // No source name or short description provided in title
            {
                type: 'title',
                regex: ".*<(Source Name|short description)>*",
                message: "You did not fill out the description in the title"
            },

            // Body acknowledgement section not removed
            {
                type: 'body',
                regex: ".*DELETE THIS SECTION IF YOU HAVE READ AND ACKNOWLEDGED IT.*",
                message: "The acknowledgment section was not removed"
            },

            // Body requested information not filled out
            {
                type: 'body',
                regex: ".*\\* (Tachiyomi version|Android version|Device|Name|Link|Extension version): \\?.*",
                message: "The requested information was not filled out"
            }
        ];

        const results = rules
            .map(rule => {
                const text = rule.type === 'title' ? payload?.issue?.title : payload?.issue?.body;
                const regexMatches: boolean = check(rule.regex, text);

                if (regexMatches) {
                    return rule.message;
                }
            })
            .filter(Boolean);

        if (results.length > 0) {
            // Comment and close
            const message = ['@${issue.user.login} this issue was automatically closed because:\n', ...results].join('\n- ');

            await client.issues.createComment({
                owner: issue.owner,
                repo: issue.repo,
                issue_number: issue.number,
                body: evalTemplate(message, payload)
            });

            await client.issues.update({
                owner: issue.owner,
                repo: issue.repo,
                issue_number: issue.number,
                state: 'closed'
            });
        }
    } catch (error) {
        core.setFailed(error.message);
    }
}

function check(patternString: string, text: string | undefined): boolean {
    const pattern: RegExp = new RegExp(patternString);

    if (text?.match(pattern)) {
        return true;
    } else {
        return false;
    }
}

function evalTemplate(template: string, params: any) {
    return Function(...Object.keys(params), `return \`${template}\``)(...Object.values(params));
}

run();
