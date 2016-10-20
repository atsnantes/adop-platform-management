// Constants
def platformToolsGitURL = "ssh://jenkins@bitbucket:7999/platform/platform-management"

def platformManagementFolderName= "/Platform_Management"
def platformManagementFolder = folder(platformManagementFolderName) { displayName('Platform Management') }

// Jobs
def loadCartridgeJob = freeStyleJob(platformManagementFolderName + "/Load_Cartridge_List")
 
// Setup setup_cartridge
loadCartridgeJob.with{
    wrappers {
        preBuildCleanup()
        sshAgent('adop-jenkins-master')
    }
    steps {
        shell('''#!/bin/bash -ex

# We trust everywhere
#echo -e "#!/bin/sh
#exec ssh -o StrictHostKeyChecking=no "\$@"
#" > ${WORKSPACE}/custom_ssh
#chmod +x ${WORKSPACE}/custom_ssh
#export GIT_SSH="${WORKSPACE}/custom_ssh"

# Init globale var
project_key="platform"

# Create repositories
mkdir ${WORKSPACE}/tmp
cd ${WORKSPACE}/tmp
while read repo_url; do
    if [ ! -z "${repo_url}" ]; then
        repo_name=$(echo "${repo_url}" | rev | cut -d'/' -f1 | rev | sed 's#.git$##g')
        target_repo_name="${repo_name}"
        
        # Init basic auth
        bitbucket_token=$(echo -n "$BITBUCKET_ADMIN_USERNAME:$BITBUCKET_ADMIN_PASSWORD" | base64)
        
        # Check if repo platform exists
        repo_exists_return_code=$(curl -s -o /dev/null -w '%{http_code}' -X GET -H "Authorization: Basic $bitbucket_token" -H "Content-Type: application/json" http://bitbucket:7990/bitbucket/rest/api/1.0/projects/$project_key/repos/$target_repo_name)
        
        if [ "$repo_exists_return_code" -eq "404" ]; then
        
cat <<EOF > repo.json
{
   "name":"$target_repo_name",
   "scmId":"git", 
   "forkable":true 
}
EOF

          curl -o /dev/null -X POST -H "Authorization: Basic $bitbucket_token" -H "Content-Type: application/json" -d @repo.json http://bitbucket:7990/bitbucket/rest/api/1.0/projects/$project_key/repos
        else
          echo "Repo already exists, continue ..."
        fi

        # Populate repository
        git clone ssh://jenkins@bitbucket:7999/$project_key/${target_repo_name}
        cd "${repo_name}"
        git remote add source "${repo_url}"
        git fetch source
        git push origin +refs/remotes/source/*:refs/heads/*
        cd -
    fi
done < ${WORKSPACE}/platform-management/cartridges.txt''')
    }
    scm {
        git {
            remote {
                name("origin")
                url("${platformToolsGitURL}")
                credentials("adop-jenkins-master")
            }
            branch("*/feature_bitbucket")
            relativeTargetDir('platform-management')
        }
    }
} 
