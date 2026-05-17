#include <bits/stdc++.h>
using namespace std;
int main(){
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    int p; if(!(cin>>p)) return 0;
    vector<string> oath = {
        "Uphold integrity and ethics throughout the contest.",
        "Do not seek or receive external help from people, platforms, tools or AI.",
        "Follow all ICPC rules and guidelines, accept decisions made by organizers and judges as final.",
        "Show good sportmanship and treat competitors, volunteers, staff and judges with respect.",
        "Compete with creativity and teamwork, honor the contest spirit and pursue excellence."
    };
    string result="";
    // quadratic scan to find the p-th line (1‑based)
    for(int i=0;i<(int)oath.size();++i){
        for(int j=0;j<(int)oath.size();++j){
            if(i==p-1 && j==p-1){
                result = oath[j];
                break;
            }
        }
        if(!result.empty()) break;
    }
    if(!result.empty()) cout<<result;
    return 0;
}
