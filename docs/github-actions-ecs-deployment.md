# GitHub Actions ECS 배포 설정

`.github/workflows/deploy-ecs.yml`은 `main` 브랜치에 병합된 커밋의 SHA를 이미지 태그로 사용한다. API, Worker, Batch 이미지를 ECR에 푸시한 뒤 각각의 ECS Service를 새 Task Definition revision으로 순서대로 배포한다.

## GitHub Repository Variables

GitHub 저장소의 **Settings > Secrets and variables > Actions > Variables**에 다음 값을 등록한다.

| 변수 | 값 |
| --- | --- |
| `AWS_DEPLOY_ROLE_ARN` | `github-actions-otboo-deploy-role`의 ARN |
| `AWS_REGION` | `ap-northeast-2` |
| `ECS_CLUSTER` | ECS Cluster 이름 |
| `ECS_API_TASK_DEFINITION` | API Task Definition family 이름 |
| `ECS_API_CONTAINER_NAME` | API 컨테이너 이름 |
| `ECS_API_SERVICE` | API ECS Service 이름 |
| `ECS_WORKER_TASK_DEFINITION` | Worker Task Definition family 이름 |
| `ECS_WORKER_CONTAINER_NAME` | Worker 컨테이너 이름 |
| `ECS_WORKER_SERVICE` | Worker ECS Service 이름 |
| `ECS_BATCH_TASK_DEFINITION` | Batch Task Definition family 이름 |
| `ECS_BATCH_CONTAINER_NAME` | Batch 컨테이너 이름 |
| `ECS_BATCH_SERVICE` | Batch ECS Service 이름 |
| `DEPLOY_ENABLED` | 준비 중에는 `false`, 자동 배포를 시작할 때 `true` |

Task Definition family와 Service 이름은 ECS 콘솔에 표시되는 이름을 그대로 입력한다. 컨테이너 이름은 Task Definition의 Container details에 입력한 이름과 정확히 같아야 한다.

AWS 런타임 비밀값은 GitHub Variables나 Secrets에 복사하지 않는다. Task Definition에서 기존처럼 AWS Secrets Manager `ValueFrom`을 참조한다.

## 활성화 전 확인

1. API, Worker, Batch ECS Service가 모두 생성되어 있어야 한다.
2. GitHub Actions IAM Role에는 ECR push, ECS Task Definition 등록 및 Service 갱신, ECS runtime role에 대한 `iam:PassRole` 권한이 있어야 한다.
3. 모든 Repository Variable이 등록된 상태에서 `DEPLOY_ENABLED`를 `true`로 바꾼다.
4. GitHub Actions 화면에서 **Deploy backend to ECS** workflow를 수동 실행해 첫 배포를 확인한다.

`DEPLOY_ENABLED=true` 이후에는 `main` 병합마다 workflow가 자동 실행된다.
