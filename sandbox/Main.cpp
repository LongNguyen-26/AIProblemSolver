#include <bits/stdc++.h>
using namespace std;

int main(){
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    int T; if(!(cin>>T)) return 0;
    while(T--){
        int n;cin>>n;
        vector<vector<int>> a(n, vector<int>(n));
        for(int i=0;i<n;i++) for(int j=0;j<n;j++) cin>>a[i][j];
        // find a row with at least one edge
        int row=-1;
        for(int i=0;i<n;i++){
            for(int j=0;j<n;j++) if(a[i][j]){ row=i; break; }
            if(row!=-1) break;
        }
        if(row==-1){ // should not happen as perfect matching exists
            cout<<-1<<"\n";
            continue;
        }
        vector<pair<int,int>> ops;
        for(int j=0;j<n;j++) if(a[row][j]) ops.emplace_back(row,j);
        // ops size <= n <= 50
        cout<<ops.size()<<"\n";
        for(auto &p:ops) cout<<p.first<<' '<<p.second<<"\n";
    }
    return 0;
}
