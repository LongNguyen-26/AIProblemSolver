// BUG_TYPE: Off-by-one
// BUG_DESC: Uses 1-based index to access a 0-indexed array, causing out-of-bounds for p=5
// FAILS_ON: Input p = 5 (the last valid index) leads to undefined behavior

#include <bits/stdc++.h>
using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    int p;
    if(!(cin >> p)) return 0;
    vector<string> oath = {
        "Uphold integrity and ethics throughout the contest.",
        "Do not seek or receive external help from people, platforms, tools or AI.",
        "Follow all ICPC rules and guidelines, accept decisions made by organizers and judges as final.",
        "Show good sportmanship and treat competitors, volunteers, staff and judges with respect.",
        "Compete with creativity and teamwork, honor the contest spirit and pursue excellence."
    };
    cout << oath[p] << "\n";
    return 0;
}