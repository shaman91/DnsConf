🌐 Languages: [󠁧󠁢󠁥󠁮󠁧󠁿**English**](README.md) | [**Русский**](README.ru.md)

[![Last Build](../../actions/workflows/github_action.yml/badge.svg?branch=main)](../../actions/workflows/github_action.yml)<br>

# DNS Block&Redirect Configurer

**Allows to set Redirect and Block rules to your Cloudflare and NextDNS accounts.**

**Ready-to-run via GitHub Actions.** [Video guide](https://www.youtube.com/watch?v=vbAXM_xAL5I)

## Comparison of Free Plans: NextDNS vs Cloudflare

|                         | NextDNS                                                         | Cloudflare                                                                                                           |
|-------------------------|-----------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| **DNS Query Limit**     | 300,000 per month                                               | 100,000 per day                                                                                                      |
| **IPv4 Restrictions**   | DNS queries are limited to a single IP address (can be changed) | DNS queries are strictly limited to a single IP address (automatically assigned by Cloudflare and cannot be changed) |
| **DoH / DoT / IPv6**    | Unlimited                                                       | Unlimited                                                                                                            |
| **Setup API Limits**    | 60 requests per minute                                          | Unlimited                                                                                                            |
| **General Limitations** | None                                                            | Infrastructure is blocked by Roskomnadzor (availability issues in Russia)                                            |
| **Advantages**          | Built-in ad and tracker blocking options                        | More reliable and fast infrastructure                                                                                |

In summary: if you are located in Russia, **NextDNS** is your only viable option due to Roskomnadzor restrictions.

If you are in another country, **Cloudflare** offers more generous limits on the free plan. Tracker and ad blocking can also
be enabled by providing a domain blocklist in `BLOCK`, for example: https://small.oisd.nl/domainswild2

## Easy Setup

Use the configurator https://dns-conf-ui.vercel.app in **Quick** mode. It will automatically configure your DNS profile
and perform all required GitHub setup steps.

You only need to sign in with GitHub and provide **CLIENT_ID** and **AUTH_SECRET**. More details on where to find these
values here: [Setup credentials](#setup-credentials)

## Standard Setup

[Setup credentials](#setup-credentials)

[Setup profile](#setup-profile)

[Setup data sources](#setup-data-sources)

[Setup exclude redirects (optional)](#setup-exclude-redirects-optional)

[Multiple profiles setup](#multiple-profiles-setup)

[GitHub Actions](#github-actions-setup)

---
## Setup credentials

### NextDNS credentials setup

1) Generate **API KEY**, from https://my.nextdns.io/account and set as **environment variable** `AUTH_SECRET`

2) Click on **NextDNS** logo. On the opened page, copy ID from Endpoints section.
   Set it as **environment variable** `CLIENT_ID`


### Cloudflare credentials setup

1) After signing up into a **Cloudflare**, navigate to _Zero Trust_ tab and create an account.

- Free Plan has decent limits, so just choose it.
- Skip providing payment method step by choosing _Cancel and exit_ (top right corner)
- Go back to _Zero Trust_ tab

2) Create a **Cloudflare API token**, from https://dash.cloudflare.com/profile/api-tokens

with 2 permissions:

    Account.Zero Trust : Edit

    Account.Account Firewall Access Rules : Edit

Set API token to **environment variable** `AUTH_SECRET`

3) Get your **Account ID** from : https://dash.cloudflare.com/?to=/:account/workers

Set **Account ID** to **environment variable** `CLIENT_ID`

---

## Setup profile

Set **environment variable** `DNS` with DNS provider name (**Cloudflare** or **NextDNS**)

---

## Setup data sources

Each data source must be a link to a hosts file,
e.g. https://raw.githubusercontent.com/Internet-Helper/GeoHideDNS/refs/heads/main/hosts/hosts

You can provide multiple sources split by coma:
https://first.com/hosts,https://second.com/hosts

### 1) Setup Redirects

Set sources to **environment variable** `REDIRECT`

Script will parse sources, filtering out redirects to `0.0.0.0` and `127.0.0.1`

Thus, parsing lines:

    0.0.0.0 domain.to.block
    1.2.3.4 domain.to.redirect
    127.0.0.1 another.to.block

will keep only `1.2.3.4 domain.to.redirect` for the further redirect processing.

+ Redirect priority follows sources order. If domain appears more than one time, the first only IP will be applied.

#### NextDNS hostname targets

`REDIRECT` also accepts `target-hostname source-domain`, for example:

```text
ai-pool.comss.one openai.com
ai-pool.comss.one chatgpt.com
ai-pool.comss.one oaiusercontent.com
```

This is a DnsConf extension, **not Windows hosts syntax**. NextDNS resolves the target;
the importer does not pin its current IP. A parent covers the domain and descendants.
An earlier hostname-target parent suppresses later child entries (including later
sources). Put explicit child exceptions before that parent. Literal-IP parent/child
behavior and first exact-domain match are unchanged. Exclusions still apply.
Malformed hostname pairs and rewrite cycles abort before rewrite cleanup.
Cloudflare rejects hostname targets before changing its rules or lists.
`BLOCK` keeps the original IP/domain syntax and does not accept CNAME pairs.

Do not publish hostname rows before the updated importer. With `PRUNE_REDIRECT=true`,
an old importer would skip those rows and could remove working rewrites.

Offline review of a complete proposed source set (all arguments are local files):

```text
java --enable-preview -cp target/dns-block-and-redirect-configurer-1.0-SNAPSHOT.jar com.novibe.dns.next_dns.RewriteDryRun existing.json exclusions.txt source1.txt source2.txt
```

`existing.json` is an array of `{ "name": "example.com", "content": "1.2.3.4" }`.
`exclusions.txt` contains one EXCLUDE_REDIRECT entry per line. The JSON result lists
adds, removals, target changes and the expected full profile for pruning enabled.
The tool makes no network calls and writes no files; it does not apply the plan.

### 2) Setup Blocklist

Set sources to **environment variable** `BLOCK`

Script will parse sources, keeping only redirects to `0.0.0.0`, `127.0.0.1`, `::1`, and also lines containing domain only.

Thus, parsing lines

    1.2.3.4 domain.to.redirect
    0.0.0.0 domain.to.block
    127.0.0.1 another.to.block
    ::1 ipv6.to.block
    no-ip.just.domain

will keep only 

    domain.to.block
    another.to.block
    no-ip.just.domain
    ipv6.to.block
for the further block processing.

+ You may want to provide the same source for both `BLOCK` and `REDIRECT` for **Cloudflare**.
+ For **NextDNS**, the best option might be to set `REDIRECT` only, and then manually choose any blocklists at the
  _Privacy_ tab.

---

## Setup exclude redirects (optional)

Put domains to **environment variable** `EXCLUDE_REDIRECT` separated by coma, e.g. `instagram.com,twitch.com`

These domains and their subdomains:

- will be removed from existing redirect rules;
- won't be added with new ones.

Prefix a domain with `=` to exclude only that exact domain while keeping its subdomains, e.g. `=tiktok.com`.

---

## Multiple profiles setup

### Restrictions

All profiles get _similar_ settings. That means `BLOCK`, `REDIRECT` and `EXCLUDE_REDIRECT` are **shared**.

### Multiple profiles of single provider

Put your profiles separated by coma **without whitespace** into related **environment variables**.
E.g., two NextDNS profiles must be set as shown:

- `AUTH_SECRET` has: `secret_NextDns_1,secret_NextDns_2`
- `CLIENT_ID` has `client_id_NextDns_1,client_id_NextDns_2`

### Multiple profiles of different providers

In addition to setting above, list provider for each profile in **environment variable** `DNS`. For example:

- `DNS` has: `NEXTDNS,CLOUDFLARE,NEXTDNS`
- `AUTH_SECRET` has: `secret_NextDns_1,secret_Cloudflare_1,secret_NextDns_2`
- `CLIENT_ID` has `client_id_NextDns_1,client_id_Cloudflare_1,client_id_NextDns_2`

---

## Script Behaviour

### Cloudflare

Previously generated data will be removed. Script recognizes old data by marks:

+ Name prefix for List: **_Blocked websites by script_** and **_Override websites by script_**
+ Name prefix for Rule: **_Rules set by script_**
+ Different **_Session id_**. **_Session id_** is stored in a description field.

After removing old data, new lists and rules will be generated and applied.

If you want to clear **Cloudflare** block/redirect settings, launch the script without providing sources in related *
*environment variables**. E.g. providing no value for **environment variable** `BLOCK` will cause removing old related
data: lists and rules used to setup blocks.

### NextDNS

For `REDIRECT`:

+ Existing domain will be updated if redirect IP has changed
+ If new domains are provided, they will be added
+ The rest redirect settings are kept untouched
+ Set optional environment variable `PRUNE_REDIRECT=true` to remove existing rewrites that are absent from the current
  `REDIRECT` sources. The default is `false`, so manually managed rewrites are preserved unless exact synchronization is
  explicitly enabled.

For `BLOCK`:

+ If new domains are provided, they will be added
+ The rest block settings are kept untouched

Previously generated data is removed **ONLY** when both `BLOCK` and `REDIRECT` sources were not provided.

---

## GitHub Actions setup

#### Step-by-step video guide: [REDIRECT for NextDNS](https://www.youtube.com/watch?v=vbAXM_xAL5I)

#### Steps

1) Fork repository
2) Go _Settings_ => _Environments_
3) Create _New environment_ with name `DNS`
4) Provide `AUTH_SECRET` and `CLIENT_ID` to **Environment secrets**
5) Provide `DNS`,`REDIRECT`, `BLOCK` and `EXCLUDE_REDIRECT` to **Environment variables**

+ The action will be launched every day at **01:30 UTC**. To set another time, change cron at
  `.github/workflows/github_action.yml`
+ You can run the action manually via `Run workflow` button: switch to _Actions_ tab and choose workflow named **DNS
  Block&Redirect Configurer cron task**
